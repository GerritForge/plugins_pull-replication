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

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.resetToDefault;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import java.net.URISyntaxException;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class ReplicationStateTest {

  private ReplicationState replicationState;
  private FetchResultProcessing fetchResultProcessingMock;

  @Before
  public void setUp() throws Exception {
    fetchResultProcessingMock = createNiceMock(FetchResultProcessing.class);
    replay(fetchResultProcessingMock);
    replicationState = new ReplicationState(fetchResultProcessingMock);
  }

  @Test
  public void shouldNotHavePushTask() {
    assertThat(replicationState.hasFetchTask()).isFalse();
  }

  @Test
  public void shouldHavePushTask() {
    replicationState.increaseFetchTaskCount("someProject", "someRef");
    assertThat(replicationState.hasFetchTask()).isTrue();
  }

  @Test
  public void shouldFireOneReplicationEventWhenNothingToReplicate() {
    resetToDefault(fetchResultProcessingMock);

    // expected event
    fetchResultProcessingMock.onAllRefsReplicatedFromAllNodes(0);
    replay(fetchResultProcessingMock);

    // actual test
    replicationState.markAllFetchTasksScheduled();
    verify(fetchResultProcessingMock);
  }

  @Test
  public void shouldFireEventsForReplicationOfOneRefToOneNode() throws URISyntaxException {
    resetToDefault(fetchResultProcessingMock);
    URIish uri = new URIish("git://someHost/someRepo.git");

    // expected events
    fetchResultProcessingMock.onOneProjectReplicationDone(
        "someProject",
        "someRef",
        uri,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    fetchResultProcessingMock.onRefReplicatedFromAllNodes("someProject", "someRef", 1);
    fetchResultProcessingMock.onAllRefsReplicatedFromAllNodes(1);
    replay(fetchResultProcessingMock);

    // actual test
    replicationState.increaseFetchTaskCount("someProject", "someRef");
    replicationState.markAllFetchTasksScheduled();
    replicationState.notifyRefReplicated(
        "someProject",
        "someRef",
        uri,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    verify(fetchResultProcessingMock);
  }

  @Test
  public void shouldFireEventsForReplicationOfMultipleRefsToMultipleNodes()
      throws URISyntaxException {
    resetToDefault(fetchResultProcessingMock);
    URIish uri1 = new URIish("git://host1/someRepo.git");
    URIish uri2 = new URIish("git://host2/someRepo.git");
    URIish uri3 = new URIish("git://host3/someRepo.git");

    // expected events
    fetchResultProcessingMock.onOneProjectReplicationDone(
        "someProject",
        "ref1",
        uri1,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    fetchResultProcessingMock.onOneProjectReplicationDone(
        "someProject",
        "ref1",
        uri2,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    fetchResultProcessingMock.onOneProjectReplicationDone(
        "someProject",
        "ref1",
        uri3,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    fetchResultProcessingMock.onOneProjectReplicationDone(
        "someProject",
        "ref2",
        uri1,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    fetchResultProcessingMock.onOneProjectReplicationDone(
        "someProject",
        "ref2",
        uri2,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);

    fetchResultProcessingMock.onRefReplicatedFromAllNodes("someProject", "ref1", 3);
    fetchResultProcessingMock.onRefReplicatedFromAllNodes("someProject", "ref2", 2);
    fetchResultProcessingMock.onAllRefsReplicatedFromAllNodes(5);
    replay(fetchResultProcessingMock);

    // actual test
    replicationState.increaseFetchTaskCount("someProject", "ref1");
    replicationState.increaseFetchTaskCount("someProject", "ref1");
    replicationState.increaseFetchTaskCount("someProject", "ref1");
    replicationState.increaseFetchTaskCount("someProject", "ref2");
    replicationState.increaseFetchTaskCount("someProject", "ref2");
    replicationState.markAllFetchTasksScheduled();

    replicationState.notifyRefReplicated(
        "someProject",
        "ref1",
        uri1,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    replicationState.notifyRefReplicated(
        "someProject",
        "ref1",
        uri2,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    replicationState.notifyRefReplicated(
        "someProject",
        "ref1",
        uri3,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);

    replicationState.notifyRefReplicated(
        "someProject",
        "ref2",
        uri1,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    replicationState.notifyRefReplicated(
        "someProject",
        "ref2",
        uri2,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);

    verify(fetchResultProcessingMock);
  }

  @Test
  public void shouldFireEventsWhenSomeReplicationCompleteBeforeAllTasksAreScheduled()
      throws URISyntaxException {
    resetToDefault(fetchResultProcessingMock);
    URIish uri1 = new URIish("git://host1/someRepo.git");

    // expected events
    fetchResultProcessingMock.onOneProjectReplicationDone(
        "someProject",
        "ref1",
        uri1,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    fetchResultProcessingMock.onOneProjectReplicationDone(
        "someProject",
        "ref2",
        uri1,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    fetchResultProcessingMock.onRefReplicatedFromAllNodes("someProject", "ref1", 1);
    fetchResultProcessingMock.onRefReplicatedFromAllNodes("someProject", "ref2", 1);
    fetchResultProcessingMock.onAllRefsReplicatedFromAllNodes(2);
    replay(fetchResultProcessingMock);

    // actual test
    replicationState.increaseFetchTaskCount("someProject", "ref1");
    replicationState.increaseFetchTaskCount("someProject", "ref2");
    replicationState.notifyRefReplicated(
        "someProject",
        "ref1",
        uri1,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    replicationState.notifyRefReplicated(
        "someProject",
        "ref2",
        uri1,
        ReplicationState.RefFetchResult.SUCCEEDED,
        RefUpdate.Result.NEW);
    replicationState.markAllFetchTasksScheduled();
    verify(fetchResultProcessingMock);
  }

  @Test
  public void toStringRefPushResult() throws Exception {
    assertEquals("failed", ReplicationState.RefFetchResult.FAILED.toString());
    assertEquals("not-attempted", ReplicationState.RefFetchResult.NOT_ATTEMPTED.toString());
    assertEquals("succeeded", ReplicationState.RefFetchResult.SUCCEEDED.toString());
  }
}
