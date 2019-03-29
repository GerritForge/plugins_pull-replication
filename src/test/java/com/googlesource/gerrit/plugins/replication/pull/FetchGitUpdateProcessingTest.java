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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.googlesource.gerrit.plugins.replication.pull.FetchResultProcessing.GitUpdateProcessing;
import com.googlesource.gerrit.plugins.replication.pull.ReplicationState.RefFetchResult;

import java.net.URISyntaxException;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class FetchGitUpdateProcessingTest {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private EventDispatcher dispatcherMock;
  private GitUpdateProcessing gitUpdateProcessing;

  @Before
  public void setUp() throws Exception {
    dispatcherMock = createMock(EventDispatcher.class);
    replay(dispatcherMock);
    ReviewDb reviewDbMock = createNiceMock(ReviewDb.class);
    replay(reviewDbMock);
    SchemaFactory<ReviewDb> schemaMock = createMock(SchemaFactory.class);
    expect(schemaMock.open()).andReturn(reviewDbMock).anyTimes();
    replay(schemaMock);
    gitUpdateProcessing = new GitUpdateProcessing(dispatcherMock);
  }

  @Test
  public void headRefReplicated()
      throws URISyntaxException, OrmException, PermissionBackendException {
    reset(dispatcherMock);
    FetchRefReplicatedEvent expectedEvent =
        new FetchRefReplicatedEvent(
            "someProject",
            "refs/heads/master",
            "someHost",
            RefFetchResult.SUCCEEDED,
            RefUpdate.Result.NEW);
    dispatcherMock.postEvent(FetchRefReplicatedEventEquals.eqEvent(expectedEvent));
    expectLastCall().once();
    replay(dispatcherMock);

    gitUpdateProcessing.onOneProjectReplicationDone(
        "someProject",
        "refs/heads/master",
        new URIish("git://someHost/someProject.git"),
        RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    verify(dispatcherMock);
  }

  @Test
  public void changeRefReplicated()
      throws URISyntaxException, OrmException, PermissionBackendException {
    reset(dispatcherMock);
    FetchRefReplicatedEvent expectedEvent =
        new FetchRefReplicatedEvent(
            "someProject",
            "refs/changes/01/1/1",
            "someHost",
            RefFetchResult.FAILED,
            RefUpdate.Result.REJECTED_OTHER_REASON);
    dispatcherMock.postEvent(FetchRefReplicatedEventEquals.eqEvent(expectedEvent));
    expectLastCall().once();
    replay(dispatcherMock);

    gitUpdateProcessing.onOneProjectReplicationDone(
        "someProject",
        "refs/changes/01/1/1",
        new URIish("git://someHost/someProject.git"),
        RefFetchResult.FAILED,
        RefUpdate.Result.REJECTED_OTHER_REASON);
    verify(dispatcherMock);
  }

  @Test
  public void onAllNodesReplicated() throws OrmException, PermissionBackendException {
    reset(dispatcherMock);
    FetchRefReplicationDoneEvent expectedDoneEvent =
        new FetchRefReplicationDoneEvent("someProject", "refs/heads/master", 5);
    dispatcherMock.postEvent(FetchRefReplicationDoneEventEquals.eqEvent(expectedDoneEvent));
    expectLastCall().once();
    replay(dispatcherMock);

    gitUpdateProcessing.onRefReplicatedFromAllNodes("someProject", "refs/heads/master", 5);
    verify(dispatcherMock);
  }
}
