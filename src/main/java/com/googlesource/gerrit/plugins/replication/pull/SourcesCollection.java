// Copyright (C) 2019 The Android Open Source Project
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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.replication.ReplicationConfig;
import com.googlesource.gerrit.plugins.replication.ReplicationConfig.FilterType;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

@Singleton
public class SourcesCollection {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ReplicationConfig replicationConfig;
  private final Source.Factory sourceFactory;
  private List<Source> sources;

  @Inject
  public SourcesCollection(ReplicationConfig replicationConfig, Source.Factory sourceFactory) {
    this.replicationConfig = replicationConfig;
    this.sourceFactory = sourceFactory;
  }

  private void load() throws ConfigInvalidException {
    this.sources = allSources();
  }

  public List<Source> getAll(FilterType filterType) {
    if (replicationConfig.reloadIfNeeded()) {
      try {
        load();
      } catch (ConfigInvalidException e) {
        logger.atWarning().withCause(e).log("Unable to load new sources");
      }
    }

    Predicate<? super Source> filter;
    switch (filterType) {
      case PROJECT_CREATION:
        filter = source -> source.isCreateMissingRepos();
        break;
      case PROJECT_DELETION:
        filter = source -> source.isReplicateProjectDeletions();
        break;
      case ALL:
      default:
        filter = source -> true;
        break;
    }
    return sources.stream().filter(Objects::nonNull).filter(filter).collect(toList());
  }

  public boolean isEmpty() {
    return sources.isEmpty();
  }

  List<Source> allSources() throws ConfigInvalidException {

    ImmutableList.Builder<Source> sources = ImmutableList.builder();
    for (RemoteConfig c : allRemotes()) {
      if (c.getURIs().isEmpty()) {
        continue;
      }

      // If source is not set, assume everything.
      if (c.getFetchRefSpecs().isEmpty()) {
        c.addFetchRefSpec(
            new RefSpec()
                .setSourceDestination("refs/*", "refs/*")
                .setForceUpdate(replicationConfig.isDefaultForceUpdate()));
      }

      Source source =
          sourceFactory.create(new SourceConfiguration(c, replicationConfig.getConfig()));

      if (!source.isSingleProjectMatch()) {
        for (URIish u : c.getURIs()) {
          if (u.getPath() == null || !u.getPath().contains("${name}")) {
            throw new ConfigInvalidException(
                String.format("remote.%s.url \"%s\" lacks ${name} placeholder", c.getName(), u));
          }
        }
      }

      sources.add(source);
    }

    List<Source> srcs = sources.build();
    logger.atInfo().log("%d replication sources loaded", srcs.size());
    return srcs;
  }

  private List<RemoteConfig> allRemotes() throws ConfigInvalidException {
    Config config = replicationConfig.getConfig();
    Set<String> names = config.getSubsections("remote");
    List<RemoteConfig> result = Lists.newArrayListWithCapacity(names.size());
    for (String name : names) {
      try {
        result.add(new RemoteConfig(config, name));
      } catch (URISyntaxException e) {
        throw new ConfigInvalidException(String.format("remote %s has invalid URL", name));
      }
    }
    return result;
  }

  public int shutdown() {
    int discarded = 0;
    for (Source cfg : sources) {
      discarded += cfg.shutdown();
    }
    return discarded;
  }

  public void startup(WorkQueue workQueue) throws ConfigInvalidException {
    load();
    for (Source cfg : sources) {
      cfg.start(workQueue);
    }
  }
}
