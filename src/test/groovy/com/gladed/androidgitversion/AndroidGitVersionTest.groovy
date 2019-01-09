package com.gladed.androidgitversion

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.rules.TemporaryFolder

class AndroidGitVersionTest extends GroovyTestCase {

    @Lazy
    TemporaryFolder projectFolder = {
        TemporaryFolder folder = new TemporaryFolder()
        folder.create()
        folder.newFile("build.gradle")
        return folder
    }()

    @Lazy
    Project project = {
        ProjectBuilder.builder()
                .withProjectDir(projectFolder.root)
                .build()
    }()

    Git git = {
        return Git.init().setDirectory(projectFolder.root).call();
    }()

    @Lazy
    AndroidGitVersionExtension plugin = {
        project.pluginManager.apply 'com.gladed.androidgitversion'
        def extension = project.getExtensions().getByName('androidGitVersion')
        assertTrue(extension instanceof AndroidGitVersionExtension)
        return (AndroidGitVersionExtension) extension
    }()

    void testNoGitRepo() {
        assertEquals('unknown', plugin.name())
        assertEquals(0, plugin.code())
    }

    void testNoCommits() {
        assertEquals('unknown', plugin.name())
        assertEquals(0, plugin.code())
    }

    void testNoTags() {
        addCommit()
        assert plugin.name().startsWith('untagged-1-')
        assertFalse plugin.name().contains("master") // Due to hideBranches
        assertEquals(0, plugin.code())
    }

    void testTag() {
        addCommit()
        addTag('1.0')
        assertEquals('1.0', plugin.name())
   }

    void testNonVersionTag() {
        addCommit()
        addTag('checkpoint-1')
        assert plugin.name().startsWith('untagged-1-')
        assertEquals(0, plugin.code())
    }

    void testTagPrefix() {
        addCommit()
        addTag("lib-1.0")
        plugin.prefix = "lib-"
        plugin.tagPattern = /^${plugin.prefix}[0-9]+(\\.[0-9]+){0,2}.*/
        assertEquals('1.0', plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testMultiTag() {
        addCommit()
        addTag("1.0")
        addCommit()
        addTag("1.1")
        assertEquals('1.1', plugin.name())
        assertEquals(1001000, plugin.code())
    }

    void testMultiTagOnSameCommit() {
        addCommit()
        addTag("1.10")
        addTag("1.7")
        addTag("1.90")
        addTag("1.8")
        assertEquals('1.90', plugin.name())
        assertEquals(1090000, plugin.code())
    }

    void testMultiTagOnSameCommit2() {
        addCommit()
        addTag("2.0.4-beta3")
        addTag("2.0.5-beta2")
        assertEquals("2.0.5-beta2", plugin.name())
    }

    void testLateParts() {
        addCommit()
        addTag("1.2-rc3")
        assertEquals("1.2-rc3", plugin.name())
        assertEquals(1002003, plugin.code())
    }

    void testMultiTagOnSameCommit3() {
        addCommit()
        addTag("2.0.2")
        addTag("2.0.2-rc")
        addTag("2.0.2b")
        assertEquals("2.0.2", plugin.name())
    }

    void testMultiTagWithPrefix() {
        plugin.prefix 'v'
        addCommit()
        addTag('v1.0.0-rc.1')
        addTag('v1.0.0')
        assertEquals("1.0.0", plugin.name())
    }

    void testCommitsAfterTag() {
        addCommit()
        addTag("1.0")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assertEquals(1000000, plugin.code())
    }

    void testAddDirty() {
        addCommit()
        addTag("1.0")
        new File(projectFolder.root, "build.gradle").append("// addition")
        assertEquals('1.0-dirty', plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testLightWeightTag() {
        addCommit()
        addLightweightTag("1.0")
        assertEquals('1.0', plugin.name())
    }

    void testAddNotDirty() {
        addCommit()
        addTag("1.0")
        File otherFile = new File(projectFolder.root, "build.gradle2")
        otherFile.createNewFile()
        otherFile.append("// addition")
        assertEquals('1.0', plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testOnlyInTagOutside() {
        // Add a tag outside the onlyIn folder
        addCommit()
        addTag("1.0")

        // Add a commit inside the onlyIn folder
        File subDir = new File(projectFolder.root, "sub")
        subDir.mkdirs()
        File subFile = new File(subDir, "file")
        subFile.createNewFile()
        subFile.append("// addition")
        git.add().addFilepattern("sub/file").call();
        git.commit().setMessage("new subfolder").call();

        plugin.onlyIn = "sub"
        assert plugin.name().startsWith("1.0-1-")
    }

    void testNothingInOnlyIn() {
        addCommit()
        File subDir = new File(projectFolder.root, "sub")
        plugin.onlyIn = "sub"
        assert plugin.name().startsWith("untagged")
    }

    void testOnlyInChangeOutside() {
        File subDir = new File(projectFolder.root, "sub")
        subDir.mkdirs()
        File subFile = new File(subDir, "file")
        subFile.createNewFile()
        subFile.append("// addition")
        git.add().addFilepattern("sub/file").call();
        git.commit().setMessage("new subfolder").call();
        addTag("1.0")

        // Add another commit after the tag, but outside the onlyIn subfolder
        addCommit()

        plugin.onlyIn = "sub"
        assertEquals('1.0', plugin.name())
    }

    void testWeirdBranchName() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assert plugin.name().endsWith('-release_1.x')
    }

    void testHideBranch() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        addCommit()
        assert plugin.name().startsWith("1.0-1-")
        assert plugin.name().endsWith('-release_1.x')
    }

    void testHideBranchPattern() {
        plugin.hideBranches = [ 'master', 'feature/.*' ]
        addCommit()
        addTag('1.0')
        addBranch('feature/xyz')
        addCommit()
        assert plugin.name() ==~ '1.0-1-[a-f0-9]{7}'
    }

    void testHideOnlyFullBranchPattern() {
        plugin.hideBranches = [ 'master', 'feat' ]
        addCommit()
        addTag('1.0')
        addBranch('feature')
        addCommit()
        assert plugin.name() ==~ '1.0-1-[a-f0-9]{7}-feature'
    }

    void testBaseCode() {
        addCommit()
        addTag("1.1")
        plugin.baseCode = 55555
        assertEquals(1056555, plugin.code())
    }

    void testFourPartCode() {
        addCommit()
        addTag("1.2.3.4")
        plugin.parts = 4
        assertEquals(1002003004, plugin.code())
    }

    void testTagWithSuffix() {
        addCommit()
        addTag("1.1-release")
        assertEquals(1001000, plugin.code())
        assertEquals("1.1-release", plugin.name())
    }

    void testTagPattern() {
        addCommit()
        addTag("1.0")
        addTag("2.0")
        // Ignore anything that doesn't start with 1
        plugin.tagPattern = /^1\..*/
        assertEquals("1.0", plugin.name())
        assertEquals(1000000, plugin.code())
    }

    void testTagVPattern() {
        addCommit()
        addTag("1.0")
        addTag("v2.0")
        plugin.tagPattern = /^v[0-9]+.*/
        assertEquals("v2.0", plugin.name())
        assertEquals(2000000, plugin.code())
    }

    void testUnmatchedPrefixedPattern() {
        addCommit()
        addTag("v1.0")
        plugin.tagPattern = /^v2\\.*/
        plugin.prefix = 'v'
        assert plugin.name().startsWith('untagged-1-')
    }

    void testSubmodule() {
        // Set up a base repo
        addCommit()
        addTag("1.0")
        assertEquals("1.0", plugin.name())

        TemporaryFolder libraryFolder = new TemporaryFolder()
        libraryFolder.create()
        libraryFolder.newFile("build.gradle")

        try {
            // Create a library repo with its own label
            Git libraryGit = Git.init().setDirectory(libraryFolder.root).call()
            libraryGit.add().addFilepattern("build.gradle").call()
            libraryGit.commit().setMessage("addition").call()
            libraryGit.tag().setName("2.0").call()

            // Add the library repo to the base repo as a submodule
            Repository libraryRepo = git.submoduleAdd()
                    .setPath("library")
                    .setURI(libraryGit.getRepository().getDirectory().getCanonicalPath())
                    .call()
            libraryRepo.close()

            // Add the submodule as a subproject to the base project
            Project libraryProject = ProjectBuilder.builder()
                    .withProjectDir(new File(projectFolder.root, "library"))
                    .withParent(project)
                    .build()
            libraryProject.pluginManager.apply 'com.gladed.androidgitversion'
            AndroidGitVersionExtension libraryPlugin = (AndroidGitVersionExtension) libraryProject.getExtensions().getByName('androidGitVersion')

            // Make sure the subproject gets its version number from the library repo
            // and NOT the base repo
            assertEquals("2.0", libraryPlugin.name())
        } finally {
            libraryFolder.delete()
        }
    }

    void testFormat() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        new File(projectFolder.root, "build.gradle").append("// addition 1")
        addCommit()
        new File(projectFolder.root, "build.gradle").append("// addition 2") // Dirty

        plugin.format = '%tag%%!count%%_commit%%.branch%%+dirty%'
        // Should be something like '1.0!1_10a984.release_1.x+dirty'
        assert plugin.name().startsWith('1.0!1_')
        assert plugin.name().endsWith('.release_1.x+dirty')
    }

    void testCleanFormat() {
        addCommit()
        addTag("1.0")
        addBranch("release/1.x")
        new File(projectFolder.root, "build.gradle").append("// addition 1")
        addCommit()

        plugin.format = '%tag%%!count%%(commit)%%.branch%%+dirty%'
        // Should be something like '1.0!1_10a984.release_1.x'
        assert plugin.name().startsWith('1.0!1(')
        assert plugin.name().endsWith(').release_1.x')
    }

    void testUntrackedIsNotDirty() {
        addCommit()
        addTag("1.0")
        File file = new File(projectFolder.root, "untracked.file");
        file.append("content");
        assertFalse("untracked is not dirty", plugin.name().contains("dirty"))
    }

    void testUntrackedIsDirty() {
        addCommit()
        addTag("1.0")
        plugin.untrackedIsDirty = true
        File file = new File(projectFolder.root, "untracked.file");
        file.append("content");
        assertTrue("untracked is dirty", plugin.name().contains("dirty"))
    }

    void testMajorMinorPatch() {
        addCommit()
        addTag("1.2")
        plugin.codeFormat = "MMNNPPP"
        assertEquals(102000, plugin.code())
    }

    void testMajorMinorPatchBuild() {
        addCommit()
        addTag("1.2")
        plugin.codeFormat = "MMNNPPBBB"
        assertEquals(10200000, plugin.code())
    }

    void testMajorMinorPatchBuildNonZero() {
        addCommit()
        addTag("42.88.33")
        addCommit()
        plugin.codeFormat = "MMNNPPBBB"
        assertEquals(428833001, plugin.code())
    }

    void testCodeFormatBaseCode() {
        plugin.codeFormat = "MNNPP"
        plugin.baseCode = 200000
        addCommit()
        addTag("1.2.3")
        assertEquals(210203, plugin.code())
    }

    void testCodeFormatExtraParts() {
        plugin.codeFormat = "MNNPP"
        addCommit()
        addTag("1.2.3.4.5.6")
        assertEquals(10203, plugin.code())
    }

    void testNearestTag() {
        addCommit()
        addTag("1.0-dev")
        addBranch("release")
        addCommit()
        def releaseCommit = addCommit()
        addTag("1.1-final")
        checkout("master")
        addCommit()
        addCommit()
        merge(releaseCommit)
        // This shows we are picking the "wrong" tag when merged, issue #34
        assertEquals("1.1-final", plugin.name())
    }

    void testLongCommitHash() {
        addCommit()
        addTag("1.4")
        addCommit()
        addCommit()
        plugin.format = "%tag%%-count%%-branch%%-dirty%"
        assertEquals("1.4-2", plugin.name())
    }

    enum AbiType { ONE, TWO, THREE }

    class SimOutput {
        int versionCodeOverride = 0
        AbiType abi
        AbiType getFilter(f) {
            if (f == "ABI") return abi
            return null
        }
    }

    class SimVariants {
        def variants
        void all(Closure c) {
            variants.each {
                c it
            }
        }
    }

    class SimVariant {
        def outputs
    }

    void testVariants() {
        plugin.codeFormat = "AMNNPP"
        addCommit()
        addTag("1.2.3")

        // Configure our own abi map
        plugin.abis = [ (AbiType.ONE) : 1, (AbiType.TWO) : 2, (AbiType.THREE) : 3 ]

        // Simulate some variants each with their own set of abi outputs
        def simVariants = new SimVariants(variants : [
                new SimVariant(outputs : [new SimOutput()]),
                new SimVariant(outputs : [new SimOutput(abi: AbiType.TWO),
                                          new SimOutput(abi: AbiType.THREE)] )
        ])

        plugin.variants(simVariants)
        assertEquals(10203, plugin.code())
        assertEquals(10203, simVariants.variants[0].outputs[0].versionCodeOverride)
        assertEquals(210203, simVariants.variants[1].outputs[0].versionCodeOverride)
        assertEquals(310203, simVariants.variants[1].outputs[1].versionCodeOverride)
    }

    void testFlush() {
        addCommit()
        addTag("1.2.3")
        assertEquals(1002003, plugin.code())
        addTag("1.2.4")
        plugin.flush()
        assertEquals(1002004, plugin.code())
    }

    void testEmpties() {
        plugin.codeFormat = "MXNNXPP"
        addCommit()
        addTag("1.2.3")
        assertEquals(1002003, plugin.code())
    }

    // Utility methods

    private RevCommit addCommit() {
        new File(projectFolder.root, "build.gradle").append("// addition")
        git.add().addFilepattern("build.gradle").call()
        git.commit().setMessage("addition").call()
    }

    private void addTag(String tagName) {
        git.tag().setName(tagName).call()
    }

    private void addLightweightTag(String tagName) {
        git.tag().setName(tagName).setAnnotated(false).call()
    }

    private void addBranch(String branchName) {
        git.checkout().setCreateBranch(true).setName(branchName).call()
    }

    private void checkout(String branchName) {
        git.checkout().setName(branchName).call()
    }

    private void merge(AnyObjectId from) {
        git.merge().setCommit(true).include(from).call()
    }

    @Override
    protected void tearDown() throws Exception {
        projectFolder.delete()
    }
}
