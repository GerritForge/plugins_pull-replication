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
import com.googlesource.gerrit.plugins.replication.pull.ReplicationConfig.FilterType;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages automatic replication from remote repositories. */
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
  private final ReplicationConfig config;
  private volatile boolean running;

  @Inject
  PullReplicationQueue(WorkQueue wq, ReplicationConfig rc, ReplicationStateListener sl) {
    workQueue = wq;
    config = rc;
    fetchStateLog = sl;
  }

  @Override
  public void start() {
    if (!running) {
      config.startup(workQueue);
      running = true;
    }
  }

  @Override
  public void stop() {
    running = false;
    int discarded = config.shutdown();
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

    for (Source cfg : config.getSources(FilterType.ALL)) {
      if (cfg.wouldFetchProject(project)) {
        for (URIish uri : cfg.getURIs(project, urlMatch)) {
          cfg.schedule(project, FetchOne.ALL_REFS, uri, state, now);
        }
      }
    }
  }
}
