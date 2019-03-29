// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.replication.pull;

import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;

public class FetchRefReplicatedEventEquals implements IArgumentMatcher {

  private FetchRefReplicatedEvent expected;

  public FetchRefReplicatedEventEquals(FetchRefReplicatedEvent expected) {
    this.expected = expected;
  }

  public static final FetchRefReplicatedEvent eqEvent(FetchRefReplicatedEvent refReplicatedEvent) {
    EasyMock.reportMatcher(new FetchRefReplicatedEventEquals(refReplicatedEvent));
    return null;
  }

  @Override
  public boolean matches(Object actual) {
    if (!(actual instanceof FetchRefReplicatedEvent)) {
      return false;
    }
    FetchRefReplicatedEvent actualRefReplicatedEvent = (FetchRefReplicatedEvent) actual;
    if (!equals(expected.project, actualRefReplicatedEvent.project)) {
      return false;
    }
    if (!equals(expected.ref, actualRefReplicatedEvent.ref)) {
      return false;
    }
    if (!equals(expected.sourceNode, actualRefReplicatedEvent.sourceNode)) {
      return false;
    }
    if (!equals(expected.status, actualRefReplicatedEvent.status)) {
      return false;
    }
    if (!equals(expected.refUpdateResult, actualRefReplicatedEvent.refUpdateResult)) {
      return false;
    }
    return true;
  }

  private static boolean equals(Object object1, Object object2) {
    if (object1 == object2) {
      return true;
    }
    if (object1 != null && !object1.equals(object2)) {
      return false;
    }
    return true;
  }

  @Override
  public void appendTo(StringBuffer buffer) {
    buffer.append("eqEvent(");
    buffer.append(expected.getClass().getName());
    buffer.append(" with project \"");
    buffer.append(expected.project);
    buffer.append("\" and ref \"");
    buffer.append(expected.ref);
    buffer.append("\" and targetNode \"");
    buffer.append(expected.sourceNode);
    buffer.append("\" and status \"");
    buffer.append(expected.status);
    buffer.append("\" and result \"");
    buffer.append(expected.refUpdateResult);
    buffer.append("\")");
  }
}
