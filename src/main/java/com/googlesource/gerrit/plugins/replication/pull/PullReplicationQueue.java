// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.replication.ReplicationConfig.FilterType;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages automatic replication from remote repositories. */
@Singleton
public class PullReplicationQueue implements LifecycleListener {
  static final String PULL_REPLICATION_LOG_NAME = "pull_replication_log";
  public static final Logger repLog = LoggerFactory.getLogger(PULL_REPLICATION_LOG_NAME);

  private final ReplicationStateListener fetchStateLog;

  public static String replaceName(String in, String name, boolean keyIsOptional) {
    String key = "${name}";
    int n = in.indexOf(key);
    if (0 <= n) {
      return in.substring(0, n) + name + in.substring(n + key.length());
    }
    if (keyIsOptional) {
      return in;
    }
    return null;
  }

  private final WorkQueue workQueue;
  private final SourcesCollection sourcesCollection;
  private volatile boolean running;

  @Inject
  PullReplicationQueue(WorkQueue wq, SourcesCollection ss, ReplicationStateListener sl) {
    workQueue = wq;
    fetchStateLog = sl;
    this.sourcesCollection = ss;
  }

  @Override
  public void start() {
    if (!running) {
      try {
        sourcesCollection.startup(workQueue);
      } catch (ConfigInvalidException e) {
        repLog.error("Unable to load sourcesCollection", e);
      }
      running = true;
    }
  }

  @Override
  public void stop() {
    running = false;
    int discarded = sourcesCollection.shutdown();
    if (discarded > 0) {
      repLog.warn("Canceled {} replication events during shutdown", discarded);
    }
  }

  void scheduleFullSync(Project.NameKey project, String urlMatch, ReplicationState state) {
    scheduleFullSync(project, urlMatch, state, false);
  }

  public void scheduleFullSync(
      Project.NameKey project, String urlMatch, ReplicationState state, boolean now) {
    if (!running) {
      fetchStateLog.warn("Replication plugin did not finish startup before event", state);
      return;
    }

    for (Source cfg : sourcesCollection.getAll(FilterType.ALL)) {
      if (cfg.wouldFetchProject(project)) {
        for (URIish uri : cfg.getURIs(project, urlMatch)) {
          cfg.schedule(project, FetchOne.ALL_REFS, uri, state, now);
        }
      }
    }
  }
}
