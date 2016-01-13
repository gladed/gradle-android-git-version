package com.gladed.gradle.androidgitversion

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.eclipse.jgit.diff.RawTextComparator

class AndroidGitVersion implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("androidGitVersion", AndroidGitVersionExtension, project)
        project.task('androidGitVersion') << {
            println "androidGitVersion.name\t${project.extensions.androidGitVersion.name()}"
            println "androidGitVersion.code\t${project.extensions.androidGitVersion.code()}"
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

    /** Project referenced by this plugin extension */
    private Project project

    /** Number of commits since last relevant tag */
    private int revCount

    /** Prefix hash for the current commit */
    private String commitPrefix

    /** Branch name for the current commit */
    private String branchName

    /** true if there are uncommitted changes */
    private boolean dirty

    /** Most recent version seen */
    private String lastVersion

    AndroidGitVersionExtension(Project project) {
        this.project = project
    }

    /**
     * Return a version name based on the most recent tag and
     * intervening commits if any.
     */
    final def name() {
        scan()

        String name = lastVersion
        if (name.equals("unknown")) return name

        if (revCount > 0) {
            name += "-$revCount-$branchName-$commitPrefix"
        }

        if (dirty) name += "-dirty"

        return name
    }

    /**
     * Return a version code corresponding to the most recent version
     */
    final def code() {
        scan()
        List<String> empties = (1..parts).collect { "0" }

        def versionParts = (!lastVersion ? empties : lastVersion.
                split(/[^0-9]+/) + empties)[0..2]

        return versionParts.inject(0) { result, i -> result * multiplier + i.toInteger() };
    }

    private void scan() {
        dirty = false
        lastVersion = "unknown"
        commitPrefix = ""
        branchName = ""

        Repository repo
        try {
            repo = new FileRepositoryBuilder().
                    readEnvironment().
                    findGitDir(project.rootDir).
                    build()
        } catch (IllegalArgumentException e) {
            // No repo found
            return
        }

        def git = Git.wrap(repo)
        def head = repo.getRef(Constants.HEAD).getTarget()
        // No commits?
        if (!head.getObjectId()) return

        commitPrefix = ObjectId.toString(head.getObjectId())[0..6]
        branchName = repo.getBranch()

        // Check to see if uncommitted files exist
        dirty = git.status().call().hasUncommittedChanges()

        // Calculate revCount and find lastVersion
        Iterable<RevTag> tags = git.tagList().call().collect { ref ->
            RevWalk walk = new RevWalk(repo)
            RevTag tag = walk.parseTag(ref.getObjectId())
            walk.close()
            tag
        }

        RevWalk revs = new RevWalk(repo)
        revs.setTreeFilter(TreeFilter.ANY_DIFF)
        revs.markStart(revs.parseCommit(head.getObjectId()))
        revCount = 0
        Collection<RevTag> commitTags = null

        for (RevCommit commit: revs) {

            def tagsHere = tags.findAll { tag ->
                tag.getObject().getId().equals(commit) &&
                        tag.getTagName().matches('^' + prefix + '[0-9].*$')
            }

            if (tagsHere) {
                commitTags = tagsHere
                break
            }

            // Does this commit contain a change to a file in onlyIn?
            if (containsRelevantChange(repo, commit)) {
                revCount++
            }
        }

        // No decent tags?
        if (!commitTags) {
            lastVersion = "untagged"
            return
        }

        // Convert tags to names w/o prefixes and get the last one numerically
        lastVersion = commitTags.
                collect { it.getTagName() - prefix }.
                sort(false) { a, b ->
                    [a,b]*.tokenize('.')*.collect { it as int }.with { u, v ->
                        [u,v].transpose().findResult{ x,y-> x<=>y ?: null } ?: u.size() <=> v.size()
                    }
                }.
                last()
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
}
