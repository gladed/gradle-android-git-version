package com.gladed.gradle.androidgitversion

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.eclipse.jgit.revwalk.RevObject

class AndroidGitVersion implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("androidGitVersion", AndroidGitVersionExtension, project)
        project.task('androidGitVersion') << {
            println "androidGitVersion.name\t${project.extensions.androidGitVersion.name()}"
            println "androidGitVersion.code\t${project.extensions.androidGitVersion.code()}"
        }
        project.task('androidGitVersionName') << {
            println project.extensions.androidGitVersion.name()
        }
        project.task('androidGitVersionCode') << {
            println project.extensions.androidGitVersion.code()
        }
    }
}

class AndroidGitVersionExtension {
    /**
     * Prefix used to specify special text before the tag. Useful in projects which manage
     * multiple external version names.
     */
    String prefix = ""

    /**
     * When set, only includes commits containing changes to the specified path
     */
    String onlyIn = ""

    /**
     * The amount of space allocated for each digit in the version code. For example,
     * for a multiplier of 1000 (the default), 1.2.3 would result in a version
     * code of 1002003
     */
    int multiplier = 1000

    /**
     * Number of parts expected in the version number. Defaults to 3 (as in
     * Semantic Versioning).
     */
    int parts = 3

    /**
     * Base code, added to all generated version codes. Defaults to 0
     */
    int baseCode = 0

    /**
     * Branches that should NOT be shown even if on an untagged commit
     */
    List<String> hideBranches = [ "master", "release" ]

    /** Project referenced by this plugin extension */
    private Project project
    private Results results

    AndroidGitVersionExtension(Project project) {
        this.project = project
    }

    /**
     * Return a version name based on the most recent tag and
     * intervening commits if any.
     */
    final String name() {
        if (!results) results = scan();

        String name = results.lastVersion

        if (name.equals("unknown")) return name

        if (results.revCount > 0) {
            name += "-${results.revCount}-${results.commitPrefix}"
            if (!hideBranches.contains(results.branchName)) {
                name += "-" + results.branchName.replaceAll("[^a-zA-Z0-9.-]", "_")
            }
        }

        if (results.dirty) name += "-dirty"

        return name
    }

    /**
     * Return a version code corresponding to the most recent version
     */
    final int code() {
        if (!results) results = scan();
        List<String> empties = (1..parts).collect { "0" }

        def versionParts = (!results.lastVersion ? empties : results.lastVersion.
                split(/[^0-9]+/) + empties).
                collect{ it as int }[0..2]

        return baseCode + versionParts.inject(0) { result, i -> result * multiplier + i.toInteger() };
    }

    /** Flush results in case git content changed */
    final void flush() {
        results = null
    }

    private Results scan() {
        Results results = new Results()

        Repository repo
        try {
            repo = new FileRepositoryBuilder()
                    .readEnvironment()
                    .findGitDir(project.projectDir)
                    .build()
        } catch (IllegalArgumentException e) {
            // No repo found
            return results
        }

        def git = Git.wrap(repo)
        def head = repo.getRef(Constants.HEAD).getTarget()
        // No commits?
        if (!head.getObjectId()) return results

        results.commitPrefix = ObjectId.toString(head.getObjectId())[0..6]
        results.branchName = repo.getBranch()

        // Check to see if uncommitted files exist
        results.dirty = git.status().call().hasUncommittedChanges()

        // Collect known tags
        Iterable<TagInfo> tags = getTagInfos(repo, git)

        // Review commits, counting revs until a suitable tag is found.
        RevWalk revs = new RevWalk(repo)
        revs.markStart(revs.parseCommit(head.getObjectId()))
        results.revCount = 0
        Collection<TagInfo> commitTags = null

        for (RevCommit commit: revs) {
            Collection<TagInfo> tagsHere = tags.findAll { it.getObjectId().equals(commit) }
            if (tagsHere) {
                commitTags = tagsHere
                break
            }

            // Does this commit contain a change to a file in onlyIn?
            if (containsRelevantChange(repo, commit)) {
                results.revCount++
            }
        }

        // No decent tags?
        if (!commitTags) {
            results.lastVersion = "untagged"
            return results
        }

        // Convert tags to names w/o prefixes and get the last one numerically
        results.lastVersion = commitTags.
                collect { (it.getName() - prefix) }.
                sort(false) { a, b ->
                    [a,b]*.split('[^0-9]+')*.collect { it as int }.with { u, v ->
                        [u,v].transpose().findResult{ x,y-> x<=>y ?: null } ?: u.size() <=> v.size()
                    }
                }.
                last()

        results
    }

    /** Collect all available tag information */
    private List<TagInfo> getTagInfos(Repository repo, Git git) {
        RevWalk walk = new RevWalk(repo)
        List<TagInfo> infos = git.tagList().call().findResults { ref ->
            RevObject obj = walk.parseAny(ref.getObjectId())
            TagInfo tag = null
            if (obj instanceof RevTag) {
                // Annotated tag
                tag = new TagInfo(obj.getObject().getId(), obj.getTagName())
            } else if (obj instanceof RevCommit) {
                // Lightweight tag
                tag = new TagInfo(obj.getId(),
                        Repository.shortenRefName(ref.getName()))
            }

            if (tag && tag.getName().matches('^' + prefix + '[0-9].*$')) {
                tag
            }
        }
        walk.close()
        return infos
    }

    /** Return true if this commit contains a change to the onlyIn path */
    private boolean containsRelevantChange(Repository repo, RevCommit commit) {
        if (!onlyIn) return true;

        if (commit.getParentCount() == 0) {
            TreeWalk tw = new TreeWalk(repo)
            tw.reset();
            tw.setRecursive(true)
            tw.addTree(commit.getTree())
            while(tw.next()) {
                if (tw.getPathString().startsWith(onlyIn)) return true
            }
        } else {
            RevWalk rw = new RevWalk(repo)
            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
            df.setRepository(repo);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);
            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
            for (DiffEntry diff : diffs) {
                if (diff.getOldPath().startsWith(onlyIn) || diff.getNewPath().startsWith(onlyIn)) return true
            }
        }
        return false
    }

    class TagInfo {
        ObjectId objectId
        String name
        TagInfo(ObjectId objectId, String name) {
            this.objectId = objectId
            this.name = name
        }
        ObjectId getObjectId() {
            objectId
        }
        String getName() {
            name
        }
        @Override String toString() { "TagInfo: " + objectId + ", name=" + name }
    }

    class Results {
        /** Number of commits since last relevant tag */
        int revCount = 0

        /** Prefix hash for the current commit */
        String commitPrefix

        /** Branch name for the current commit */
        String branchName

        /** true if there are uncommitted changes */
        boolean dirty = false

        /** Most recent version seen */
        String lastVersion = 'unknown'
    }
}
