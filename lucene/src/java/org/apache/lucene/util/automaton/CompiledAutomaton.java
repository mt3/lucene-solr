package org.apache.lucene.util.automaton;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
  
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.codecs.BlockTreeTermsWriter;
import org.apache.lucene.util.BytesRef;

/**
 * Immutable class holding compiled details for a given
 * Automaton.  The Automaton is deterministic, must not have
 * dead states but may not be minimal.
 *
 * @lucene.experimental
 */
public class CompiledAutomaton {
  public final ByteRunAutomaton runAutomaton;
  // TODO: would be nice if these sortedTransitions had "int
  // to;" instead of "State to;" somehow:
  public final Transition[][] sortedTransitions;
  public final BytesRef commonSuffixRef;
  public final boolean finite;

  // nocommit -- move pulling of a TermsEnum into here, so
  // that we can optimize for cases where a simpler enume
  // (prefix enum, all terms, no terms, etc.) can be used
    
  public CompiledAutomaton(Automaton automaton, boolean finite) {
    Automaton utf8 = new UTF32ToUTF8().convert(automaton);
    runAutomaton = new ByteRunAutomaton(utf8, true);
    sortedTransitions = utf8.getSortedTransitions();
    this.finite = finite;
    if (finite) {
      commonSuffixRef = null;
    } else {
      commonSuffixRef = SpecialOperations.getCommonSuffixBytesRef(utf8);
    }
  }

  private static final boolean DEBUG = BlockTreeTermsWriter.DEBUG;

  private BytesRef addTail(int state, BytesRef term, int idx, int leadLabel) {

    // Find biggest transition that's < label
    // TODO: use binary search here
    Transition maxTransition = null;
    for (Transition transition : sortedTransitions[state]) {
      if (transition.min < leadLabel) {
        maxTransition = transition;
      }
    }

    assert maxTransition != null;

    // Append floorLabel
    final int floorLabel;
    if (maxTransition.max > leadLabel-1) {
      floorLabel = leadLabel-1;
    } else {
      floorLabel = maxTransition.max;
    }
    if (idx >= term.bytes.length) {
      term.grow(1+idx);
    }
    if (DEBUG) System.out.println("  add floorLabel=" + (char) floorLabel + " idx=" + idx);
    term.bytes[idx] = (byte) floorLabel;

    state = maxTransition.to.getNumber();
    idx++;

    // Push down to last accept state
    while (true) {
      Transition[] transitions = sortedTransitions[state];
      if (transitions.length == 0) {
        assert runAutomaton.isAccept(state);
        term.length = idx;
        if (DEBUG) System.out.println("  return " + term.utf8ToString());
        return term;
      } else {
        // We are pushing "top" -- so get last label of
        // last transition:
        assert transitions.length != 0;
        Transition lastTransition = transitions[transitions.length-1];
        if (idx >= term.bytes.length) {
          term.grow(1+idx);
        }
        if (DEBUG) System.out.println("  push maxLabel=" + (char) lastTransition.max + " idx=" + idx);
        term.bytes[idx] = (byte) lastTransition.max;
        state = lastTransition.to.getNumber();
        idx++;
      }
    }
  }

  /** Finds largest term accepted by this Automaton, that's
   *  <= the provided input term.  The result is placed in
   *  output; it's fine for output and input to point to
   *  the same BytesRef.  The returned result is either the
   *  provided output, or null if there is no floor term
   *  (ie, the provided input term is before the first term
   *  accepted by this Automaton). */
  public BytesRef floor(BytesRef input, BytesRef output) {

    output.offset = 0;
    if (DEBUG) System.out.println("CA.floor input=" + input.utf8ToString());

    int state = runAutomaton.getInitialState();

    // Special case empty string:
    if (input.length == 0) {
      if (runAutomaton.isAccept(state)) {
        output.length = 0;
        return output;
      } else {
        return null;
      }
    }

    final List<Integer> stack = new ArrayList<Integer>();

    int idx = 0;
    while (true) {
      int label = input.bytes[input.offset + idx] & 0xff;
      int nextState = runAutomaton.step(state, label);
      if (DEBUG) System.out.println("  cycle label=" + (char) label + " nextState=" + nextState);

      if (idx == input.length-1) {
        if (nextState != -1 && runAutomaton.isAccept(nextState)) {
          // Input string is accepted
          if (idx >= output.bytes.length) {
            output.grow(1+idx);
          }
          output.bytes[idx] = (byte) label;
          output.length = input.length;
          if (DEBUG) System.out.println("  input is accepted; return term=" + output.utf8ToString());
          return output;
        } else {
          nextState = -1;
        }
      }

      if (nextState == -1) {

        // Pop back to a state that has a transition
        // <= our label:
        while (true) {
          Transition[] transitions = sortedTransitions[state];
          if (transitions.length == 0) {
            assert runAutomaton.isAccept(state);
            output.length = idx;
            if (DEBUG) System.out.println("  return " + output.utf8ToString());
            return output;
          } else if (label-1 < transitions[0].min) {

            if (runAutomaton.isAccept(state)) {
              output.length = idx;
              if (DEBUG) System.out.println("  return " + output.utf8ToString());
              return output;
            }
            // pop
            if (stack.size() == 0) {
              if (DEBUG) System.out.println("  pop ord=" + idx + " return null");
              return null;
            } else {
              state = stack.remove(stack.size()-1);
              idx--;
              if (DEBUG) System.out.println("  pop ord=" + (idx+1) + " label=" + (char) label + " first trans.min=" + (char) transitions[0].min);
              label = input.bytes[input.offset + idx] & 0xff;
            }

          } else {
            if (DEBUG) System.out.println("  stop pop ord=" + idx + " first trans.min=" + (char) transitions[0].min);
            break;
          }
        }

        if (DEBUG) System.out.println("  label=" + (char) label + " idx=" + idx);

        return addTail(state, output, idx, label);
        
      } else {
        if (idx >= output.bytes.length) {
          output.grow(1+idx);
        }
        output.bytes[idx] = (byte) label;
        stack.add(state);
        state = nextState;
        idx++;
      }
    }
  }
}