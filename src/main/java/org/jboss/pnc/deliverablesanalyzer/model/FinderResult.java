/*
 * Copyright (C) 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.deliverablesanalyzer.model;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.BadRequestException;

import org.jboss.pnc.build.finder.core.BuildStatistics;
import org.jboss.pnc.build.finder.core.BuildSystem;
import org.jboss.pnc.build.finder.core.BuildSystemInteger;
import org.jboss.pnc.build.finder.core.Checksum;
import org.jboss.pnc.build.finder.koji.KojiBuild;
import org.jboss.pnc.build.finder.koji.KojiLocalArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.red.build.koji.model.xmlrpc.KojiArchiveInfo;

public class FinderResult {
    private static final Logger LOGGER = LoggerFactory.getLogger(FinderResult.class);

    @NotEmpty
    @Pattern(regexp = "^[a-f0-9]{8}$")
    private String id;

    private URL url;

    @NotNull
    @Valid
    private final Set<Build> builds;

    @NotNull
    @Valid
    private final Set<Artifact> notFoundArtifacts;

    @NotNull
    @Valid
    private final BuildStatistics statistics;

    public FinderResult() {
        this.builds = Collections.emptySet();
        this.notFoundArtifacts = Collections.emptySet();
        this.statistics = new BuildStatistics(Collections.emptyList());
    }

    public FinderResult(String id, URL url, Map<BuildSystemInteger, KojiBuild> builds) {
        this.id = id;
        this.url = url;
        this.builds = getFoundBuilds(builds);
        this.notFoundArtifacts = getNotFoundArtifacts(builds);
        this.statistics = new BuildStatistics(getBuildsAsList(builds));
    }

    public String getId() {
        return id;
    }

    public URL getUrl() {
        return url;
    }

    public Set<Build> getBuilds() {
        return Collections.unmodifiableSet(builds);
    }

    public Set<Artifact> getNotFoundArtifacts() {
        return Collections.unmodifiableSet(notFoundArtifacts);
    }

    public BuildStatistics getStatistics() {
        return statistics;
    }

    private static void setArtifactChecksums(Artifact artifact, Iterable<Checksum> checksums) {
        for (var checksum : checksums) {
            switch (checksum.getType()) {
                case md5:
                    artifact.setMd5(checksum.getValue());
                    break;
                case sha1:
                    artifact.setSha1(checksum.getValue());
                    break;
                case sha256:
                    artifact.setSha256(checksum.getValue());
                    break;
                default:
                    break;
            }
        }
    }

    private static MavenArtifact createMavenArtifact(KojiArchiveInfo archiveInfo) {
        var groupId = archiveInfo.getGroupId();
        var artifactId = archiveInfo.getArtifactId();
        var type = archiveInfo.getExtension() != null ? archiveInfo.getExtension() : "";
        var version = archiveInfo.getVersion();
        var classifier = archiveInfo.getClassifier() != null ? archiveInfo.getClassifier() : "";
        var mavenArtifact = new MavenArtifact();

        mavenArtifact.setGroupId(groupId);
        mavenArtifact.setArtifactId(artifactId);
        mavenArtifact.setType(type);
        mavenArtifact.setVersion(version);
        mavenArtifact.setClassifier(classifier);

        return mavenArtifact;
    }

    private static NpmArtifact createNpmArtifact(KojiArchiveInfo archiveInfo) {
        var name = archiveInfo.getArtifactId();
        var version = archiveInfo.getVersion();
        var npmArtifact = new NpmArtifact();

        npmArtifact.setName(name);
        npmArtifact.setVersion(version);

        return npmArtifact;
    }

    private static Artifact createNotFoundArtifact(KojiLocalArchive localArchive) {
        var artifact = new Artifact();

        setArtifactChecksums(artifact, localArchive.getChecksums());

        artifact.setBuiltFromSource(Boolean.FALSE);
        artifact.setFilesNotBuiltFromSource(new TreeSet<>(localArchive.getFilenames()));

        return artifact;
    }

    private static Set<Artifact> getNotFoundArtifacts(Map<BuildSystemInteger, KojiBuild> builds) {
        var buildsSize = builds.size();

        if (buildsSize == 0) {
            return Collections.unmodifiableSet(new LinkedHashSet<>());
        }

        var buildZero = builds.get(new BuildSystemInteger(0));
        var localArchives = buildZero.getArchives();
        var numArchives = localArchives.size();

        if (numArchives == 0) {
            return Collections.unmodifiableSet(new LinkedHashSet<>());
        }

        var artifacts = (Set<Artifact>) new LinkedHashSet<Artifact>(numArchives);
        var archiveCount = 0;

        for (var localArchive : localArchives) {
            var artifact = createNotFoundArtifact(localArchive);

            artifacts.add(artifact);

            archiveCount++;

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(
                        "Not found artifact: {} / {} ({})",
                        archiveCount,
                        numArchives,
                        artifact.getFilesNotBuiltFromSource());
            }
        }

        return Collections.unmodifiableSet(artifacts);
    }

    private static Build createBuild(BuildSystemInteger buildSystemInteger, KojiBuild kojiBuild) {
        BuildSystemType buildSystemType;

        if (buildSystemInteger.getBuildSystem() == BuildSystem.pnc) {
            buildSystemType = BuildSystemType.PNC;
        } else {
            buildSystemType = BuildSystemType.KOJI;
        }

        var identifier = kojiBuild.getBuildInfo().getNvr();
        var build = new Build();

        build.setIdentifier(identifier);
        build.setBuildSystemType(buildSystemType);

        if (build.getBuildSystemType() == BuildSystemType.PNC) {
            build.setPncId((long) kojiBuild.getBuildInfo().getId());
        } else {
            build.setKojiId((long) kojiBuild.getBuildInfo().getId());
        }

        build.setSource(kojiBuild.getSource().orElse(null));

        build.setBuiltFromSource(!kojiBuild.isImport());

        return build;
    }

    private static Artifact createArtifact(KojiLocalArchive localArchive, Build build) {
        var archiveInfo = localArchive.getArchive();
        var mavenArtifact = (MavenArtifact) null;
        var npmArtifact = (NpmArtifact) null;
        var artifactIdentifier = (String) null;

        if ("maven".equals(archiveInfo.getBuildType())) {
            mavenArtifact = createMavenArtifact(archiveInfo);
            artifactIdentifier = mavenArtifact.getIdentifier();
        } else if ("npm".equals(archiveInfo.getBuildType())) {
            npmArtifact = createNpmArtifact(archiveInfo);
            artifactIdentifier = npmArtifact.getIdentifier();
        } else {
            throw new BadRequestException(
                    "Archive " + archiveInfo.getArtifactId() + " had unhandled artifact type: "
                            + archiveInfo.getBuildType());
        }

        var artifact = new Artifact();

        artifact.setIdentifier(artifactIdentifier);
        artifact.setBuildSystemType(build.getBuildSystemType());

        if (localArchive.isBuiltFromSource()) {
            artifact.setBuiltFromSource(build.getBuiltFromSource());
        } else {
            artifact.setBuiltFromSource(Boolean.FALSE);
            artifact.getFilesNotBuiltFromSource().addAll(localArchive.getUnmatchedFilenames());
        }

        setArtifactChecksums(artifact, localArchive.getChecksums());

        if (build.getBuildSystemType() == BuildSystemType.PNC) {
            artifact.setPncId(Long.valueOf(archiveInfo.getArchiveId()));
        } else {
            artifact.setKojiId(Long.valueOf(archiveInfo.getArchiveId()));
        }

        artifact.setBuild(build);

        if (mavenArtifact != null) {
            mavenArtifact.setArtifact(artifact);
            artifact.setMavenArtifact(mavenArtifact);
            artifact.setType(Artifact.Type.MAVEN);
        } else {
            npmArtifact.setArtifact(artifact);
            artifact.setNpmArtifact(npmArtifact);
            artifact.setType(Artifact.Type.NPM);
        }

        return artifact;
    }

    private static Set<Build> getFoundBuilds(Map<BuildSystemInteger, KojiBuild> builds) {
        var buildsSize = builds.size();

        if (buildsSize <= 1) {
            return Collections.unmodifiableSet(new LinkedHashSet<>());
        }

        var numBuilds = buildsSize - 1;
        var buildList = (Set<Build>) new LinkedHashSet<Build>(numBuilds);
        var buildCount = 0;
        var entrySet = builds.entrySet();

        for (Map.Entry<BuildSystemInteger, KojiBuild> entry : entrySet) {
            BuildSystemInteger buildSystemInteger = entry.getKey();

            if (buildSystemInteger.getValue().equals(0)) {
                continue;
            }

            var kojiBuild = entry.getValue();
            var build = createBuild(buildSystemInteger, kojiBuild);

            if (LOGGER.isInfoEnabled()) {
                buildCount++;

                LOGGER.info(
                        "Build: {} / {} ({}.{})",
                        buildCount,
                        numBuilds,
                        build.getIdentifier(),
                        build.getBuildSystemType());
            }

            var localArchives = kojiBuild.getArchives();
            var numArchives = localArchives.size();
            var archiveCount = 0;

            for (KojiLocalArchive localArchive : localArchives) {
                var artifact = createArtifact(localArchive, build);

                build.getArtifacts().add(artifact);

                if (LOGGER.isInfoEnabled()) {
                    archiveCount++;

                    LOGGER.info("Artifact: {} / {} ({})", archiveCount, numArchives, artifact.getIdentifier());
                }
            }

            buildList.add(build);
        }

        return Collections.unmodifiableSet(buildList);
    }

    private static List<KojiBuild> getBuildsAsList(Map<BuildSystemInteger, KojiBuild> builds) {
        var kojiBuildList = (List<KojiBuild>) new ArrayList<>(builds.values());

        kojiBuildList.sort(Comparator.comparingInt(KojiBuild::getId));

        return Collections.unmodifiableList(kojiBuildList);
    }
}
