// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.config.ConfigUtil;
import com.googlesource.gerrit.plugins.replication.RemoteConfiguration;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;

public class SourceConfiguration implements RemoteConfiguration {
  static final int DEFAULT_REPLICATION_DELAY = 15;
  static final int DEFAULT_RESCHEDULE_DELAY = 3;
  static final int DEFAULT_SLOW_LATENCY_THRESHOLD_SECS = 900;

  private final int delay;
  private final int rescheduleDelay;
  private final int retryDelay;
  private final int lockErrorMaxRetries;
  private final ImmutableList<String> adminUrls;
  private final int poolThreads;
  private final boolean replicatePermissions;
  private final boolean replicateHiddenProjects;
  private final String remoteNameStyle;
  private final ImmutableList<String> urls;
  private final ImmutableList<String> projects;
  private final ImmutableList<String> authGroupNames;
  private final RemoteConfig remoteConfig;
  private final int maxRetries;
  private int slowLatencyThreshold;

  public SourceConfiguration(RemoteConfig remoteConfig, Config cfg) {
    this.remoteConfig = remoteConfig;
    String name = remoteConfig.getName();
    urls = ImmutableList.copyOf(cfg.getStringList("remote", name, "url"));
    delay = Math.max(0, getInt(remoteConfig, cfg, "replicationdelay", DEFAULT_REPLICATION_DELAY));
    rescheduleDelay =
        Math.max(3, getInt(remoteConfig, cfg, "rescheduledelay", DEFAULT_RESCHEDULE_DELAY));
    projects = ImmutableList.copyOf(cfg.getStringList("remote", name, "projects"));
    adminUrls = ImmutableList.copyOf(cfg.getStringList("remote", name, "adminUrl"));
    retryDelay = Math.max(0, getInt(remoteConfig, cfg, "replicationretry", 1));
    poolThreads = Math.max(0, getInt(remoteConfig, cfg, "threads", 1));
    authGroupNames = ImmutableList.copyOf(cfg.getStringList("remote", name, "authGroup"));
    lockErrorMaxRetries = cfg.getInt("replication", "lockErrorMaxRetries", 0);

    replicatePermissions = cfg.getBoolean("remote", name, "replicatePermissions", true);
    replicateHiddenProjects = cfg.getBoolean("remote", name, "replicateHiddenProjects", false);
    remoteNameStyle =
        MoreObjects.firstNonNull(cfg.getString("remote", name, "remoteNameStyle"), "slash");
    maxRetries =
        getInt(
            remoteConfig, cfg, "replicationMaxRetries", cfg.getInt("replication", "maxRetries", 0));
    slowLatencyThreshold =
        (int)
            ConfigUtil.getTimeUnit(
                cfg,
                "remote",
                remoteConfig.getName(),
                "slowLatencyThreshold",
                DEFAULT_SLOW_LATENCY_THRESHOLD_SECS,
                TimeUnit.SECONDS);
  }

  @Override
  public int getDelay() {
    return delay;
  }

  @Override
  public int getRescheduleDelay() {
    return rescheduleDelay;
  }

  @Override
  public int getRetryDelay() {
    return retryDelay;
  }

  public int getPoolThreads() {
    return poolThreads;
  }

  public int getLockErrorMaxRetries() {
    return lockErrorMaxRetries;
  }

  @Override
  public ImmutableList<String> getUrls() {
    return urls;
  }

  @Override
  public ImmutableList<String> getAdminUrls() {
    return adminUrls;
  }

  @Override
  public ImmutableList<String> getProjects() {
    return projects;
  }

  @Override
  public ImmutableList<String> getAuthGroupNames() {
    return authGroupNames;
  }

  @Override
  public String getRemoteNameStyle() {
    return remoteNameStyle;
  }

  @Override
  public boolean replicatePermissions() {
    return replicatePermissions;
  }

  public boolean replicateHiddenProjects() {
    return replicateHiddenProjects;
  }

  @Override
  public RemoteConfig getRemoteConfig() {
    return remoteConfig;
  }

  @Override
  public int getMaxRetries() {
    return maxRetries;
  }

  private static int getInt(RemoteConfig rc, Config cfg, String name, int defValue) {
    return cfg.getInt("remote", rc.getName(), name, defValue);
  }

  @Override
  public int getSlowLatencyThreshold() {
    return slowLatencyThreshold;
  }
}
