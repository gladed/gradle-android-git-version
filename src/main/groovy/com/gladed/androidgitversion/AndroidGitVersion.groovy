package com.gladed.androidgitversion

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevObject
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidGitVersion implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("androidGitVersion", AndroidGitVersionExtension, project)
        project.task('androidGitVersion') {
            doLast {
                println "androidGitVersion.name\t${project.extensions.androidGitVersion.name()}"
                println "androidGitVersion.code\t${project.extensions.androidGitVersion.code()}"
            }
        }
        project.task('androidGitVersionName') {
            doLast {
                println project.extensions.androidGitVersion.name()
            }
        }
        project.task('androidGitVersionCode') {
            doLast {
                println project.extensions.androidGitVersion.code()
            }
        }
    }
}

class AndroidGitVersionExtension {
    /**
    * Option to make versionName match the expected output for those using `git describe`
    */
    boolean matchGitDescribe = false

    /**
     * Prefix used to specify special text before the tag. Useful in projects which manage
     * multiple external version names.
     */
    String prefix = ""

    /**
     * Search pattern for tags of interest. Tags not matching this pattern will be ignored.
     */
    String tagPattern = ""

    /**
     * When set, only includes commits containing changes to the specified path
     */
    String onlyIn = ""

    /**
     * DEPRECATED (use codeFormat instead): The amount of space allocated for each digit in the
     * version code. For example, for a multiplier of 1000, 1.2.3 would result in a version
     * code of 1002003
     */
    @Deprecated
    Integer multiplier

    /**
     * DEPRECATED (use codeFormat instead): Number of parts expected in the version number.
     */
    @Deprecated
    Integer parts

    /**
     * Base code, added to all generated version codes. Defaults to 0
     */
    int baseCode = 0

    /**
     * The number of characters to take from the front of a commit hash.
     */
    int commitHashLength = 7

    /**
     * Branches that should NOT be shown even if on an untagged commit
     */
    List<String> hideBranches = [ "master", "release" ]

    /**
     * Treat a build as dirty if there are any untracked files
     */
    boolean untrackedIsDirty = false

    /**
     * Format of version name string.
     */
    String format = '%tag%%-count%%-commit%%-branch%%-dirty%'

    /**
     * Format for versionCode output. null if user is still using deprecated fields.
     */
    String codeFormat

    /**
     * Map of ABI designators to integers.
     */
    def abis = abis = ['armeabi':1, 'armeabi-v7a':2, 'arm64-v8a':3, 'mips':5, 'mips64':6,
                       'x86':8, 'x86_64':9 ]

    def currentAbi = 0

    enum CodePart {
        EMPTY, MAJOR, MINOR, PATCH, REVISION, BUILD, ABI
    }
    private List<List> codeParts

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
        if (!results) results = scan()

        String name = results.lastVersion


        if (name == "unknown") return name
        name = this.format

        def parts = [tag: results.lastVersion]
        if (results.revCount > 0) {
            parts['count'] = results.revCount
            parts['commit'] = results.commitPrefix
            String branchName = results.branchName
            if (!hideBranches.any { branchName ==~ it }) {
                parts['branch'] = branchName.replaceAll("[^a-zA-Z0-9.-]", "_")
            }
        }
        if (results.dirty) parts['dirty'] = 'dirty'

        parts.each { part, value ->
            name = name.replaceAll(/%([^%]*)$part([^%]*)%/) { all, start, end ->
                start + value + end
            }
        }
        name = name.replaceAll(/%[^%]+%/,'')

        return name
    }

    /**
     * Return a version code corresponding to the most recent version
     */
    final int code() {
        readCodeFormat()
        if (!results) results = scan()
        if (codeParts == null) {
            // Fallback for case where no codeParts are given
            def versionParts = results.getVersionParts(parts)
            return baseCode + versionParts.inject(0) { result, i -> result * multiplier + i.toInteger() }
        } else {
            def r = results // Make available to closure
            return baseCode + codeParts.inject(0) {
                code, part -> r.addCodePart(code, part[0], part[1])
            }
        }
    }

    /** Flush results in case git content changed */
    final void flush() {
        results = null
    }

    /** Update the versionCodeOverride for all variant outputs according to ABI */
    final void variants(variants) {
        variants.all { variant ->
            variant.outputs.each { output ->
                currentAbi = abis.get(output.getFilter("ABI"), 0)
                output.versionCodeOverride = code()
                // Don't leave this value dangling, we don't know when this closure will apply
                currentAbi = 0
            }
        }
    }

    private Results scan() {
        validateSettings()
        Results results = new Results()

        Repository repo
        try {
            repo = new FileRepositoryBuilder()
                    .readEnvironment()
                    .findGitDir(project.projectDir)
                    .build()
        } catch (IllegalArgumentException ignore) {
            // No repo found
            return results
        }

        def git = Git.wrap(repo)
        def head = repo.findRef(Constants.HEAD).getTarget()
        // No commits?
        if (!head.getObjectId()) return results

        results.commitPrefix = ObjectId.toString(head.getObjectId())[0..(commitHashLength - 1)]
        results.branchName = repo.getBranch()

        // Check to see if uncommitted files exist
        results.dirty = git.status().call().hasUncommittedChanges() ||
                (untrackedIsDirty && !git.status().call().getUntracked().isEmpty())

        // Collect known tags
        Iterable<TagInfo> tags = getTagInfos(repo, git)

        // Review commits, counting revs until a suitable tag is found.
        RevWalk revs = new RevWalk(repo)
        revs.markStart(revs.parseCommit(head.getObjectId()))
        results.revCount = 0
        Collection<TagInfo> commitTags = null

        for (RevCommit commit: revs) {
            def tagsHere = tags.findAll { (it.getObjectId() == commit) }
            if (tagsHere) {
                commitTags = tagsHere
                break
            }

            // Does this commit contain a change to a file in onlyIn?
            if (containsRelevantChange(repo, commit)) {
                results.revCount++
            }
        }

        // No good tags?
        if (!commitTags) {
            results.lastVersion = "untagged"
            return results
        }

        // Convert tags to names w/o prefixes and get the shortest and last one numerically
        results.lastVersion = commitTags.
                collect { (it.getName() - prefix) }.
                sort { a, b -> [versionParts(a), versionParts(b)]
                        .transpose()
                        .findResult{ x,y-> x<=>y ?: null } ?: b.size() <=> a.size()
                }.
                last()

        results.outputOfGitDescribe = git.describe().call()

        results
    }

    // Return all numeric parts found anywhere in the string
    static int[] versionParts(String version) {
        version.split('[^0-9]+').findAll { it.length() > 0 }.collect { it as int }
    }

    private void validateSettings() {
        if (commitHashLength < 4 || commitHashLength > 40) {
            throw new GradleException("commitHashLength of $commitHashLength must be in the range of 4..20")
        }
    }

    private void readCodeFormat() {
        // Assign a default codeFormat or fall back to deprecated multiplier/parts
        if (codeFormat == null) {
            if (multiplier == null && parts == null) {
                codeFormat = "MMMNNNPPP" // Default as if parts=3 and mult=1000
            } else {
                // If either was specified, apply any missing defaults and proceed
                if (multiplier == null) multiplier = 1000
                if (parts == null) parts = 3
                return
            }
        }

        if (parts != null || multiplier != null) {
            throw new GradleException('cannot use "parts" or "multiplier" with "codeFormat"')
        }

        // Parse out code parts
        codeParts = []
        if (codeFormat.length() > 9) {
            throw new GradleException("codeFormat " + codeFormat + " is too long "
                    + " (version codes must be < 2100000000")
        }
        for (char ch: codeFormat.toCharArray()) {
            CodePart part
            switch(ch) {
                case 'M': part = CodePart.MAJOR; break
                case 'N': part = CodePart.MINOR; break
                case 'P': part = CodePart.PATCH; break
                case 'B': part = CodePart.BUILD; break
                case 'A': part = CodePart.ABI; break
                case 'X': part = CodePart.EMPTY; break
                case 'R': part = CodePart.REVISION; break
                default: throw new GradleException("Unrecognized char " + ch + " in codeFormat")
            }
            if (!codeParts.isEmpty() && codeParts[-1][0] == part) {
                codeParts[-1][1]++
            } else {
                codeParts.add([part, 1])
            }
        }
    }

    /** Collect all available tag information */
    private List<TagInfo> getTagInfos(Repository repo, Git git) {
        String searchPattern = tagPattern.isEmpty() ? /^$prefix[0-9]+.*/ : tagPattern
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

            if (tag && tag.getName().matches(searchPattern) && tag.getName().startsWith(prefix)) {
                tag
            } else {
                null
            }
        }
        walk.close()
        return infos
    }

    /** Return true if this commit contains a change to the onlyIn path */
    private boolean containsRelevantChange(Repository repo, RevCommit commit) {
        if (!onlyIn) return true

        if (commit.getParentCount() == 0) {
            TreeWalk tw = new TreeWalk(repo)
            tw.reset()
            tw.setRecursive(true)
            tw.addTree(commit.getTree())
            //noinspection ChangeToOperator - ++tw may attempt to assign a boolean to tw
            while (tw.next()) {
                if (tw.getPathString().startsWith(onlyIn)) return true
            }
        } else {
            RevWalk rw = new RevWalk(repo)
            RevCommit parent = rw.parseCommit(commit.getParent(0).getId())
            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)
            df.setRepository(repo)
            df.setDiffComparator(RawTextComparator.DEFAULT)
            df.setDetectRenames(true)
            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree())
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

        /** The resulting output from calling git describe */
        String outputOfGitDescribe = null

        List getVersionParts(int parts) {
            List<String> empties = (1..parts).collect { "0" }
            return (!lastVersion ? empties : lastVersion.
                    split(/[^0-9]+/) + empties).
                    findAll { !it.isEmpty() }.
                    collect { it as int }[0..<parts]
        }

        int getVersionPart(int index) {
            return getVersionParts(index + 1)[index]
        }

        int addCodePart(int code, CodePart part, int width) {
            def digits
            switch(part) {
                case CodePart.MAJOR: digits = getVersionPart(0); break
                case CodePart.MINOR: digits = getVersionPart(1); break
                case CodePart.PATCH: digits = getVersionPart(2); break
                case CodePart.REVISION: digits = getVersionPart(3); break
                case CodePart.BUILD: digits = revCount; break
                case CodePart.ABI: digits = currentAbi; break
                case CodePart.EMPTY: digits = 0; break
                default: throw new GradleException("Unimplemented part " + part)
            }
            if (((int)Math.log10(digits)) + 1 > width) {
                throw new GradleException("Not enough room for " + digits + " in " + part +
                        " width=" + width)
            }
            return code * Math.pow(10, width) + digits
        }
    }
}
