package com.gladed.gradle.androidgitversion

import org.eclipse.jgit.api.Git
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.rules.TemporaryFolder

class AndroidGitVersionTest extends GroovyTestCase {

    def projectFolder = new TemporaryFolder()

    // These properties don't exist until touched
    @Lazy Git git = { initGit() }()
    @Lazy AndroidGitVersionExtension plugin = { makePlugin() }()

    public void testNoGitRepo() {
        assertEquals('unknown', plugin.name())
    }

    public void testNoCommits() {
        git // touch to build
        assertEquals('unknown', plugin.name())
    }

    public void testNoTags() {
        addCommit()
        assertEquals('unknown', plugin.name())
    }

    public void testTag() {
        addCommit()
        addTag('1.0')
        assertEquals('1.0', plugin.name())
    }

    public void testNonVersionTag() {
        addCommit()
        addTag('checkpoint-1')
        assertEquals('unknown', plugin.name())
    }

    public void testTagPrefix() {
        addCommit()
        addTag("lib-1.0");
        plugin.tagPrefix = "lib-"
        assertEquals('1.0', plugin.name())
    }

    public void testMultiTag() {
        addCommit()
        addTag("1.0")
        addCommit()
        addTag("1.1")
        assertEquals('1.1', plugin.name())
    }

    public void testMultiTagOnSameCommit() {
        addCommit()
        addTag("1.10")
        addTag("1.9")
        assertEquals('1.10', plugin.name())
    }

    private Git initGit() {
        return Git.init().setDirectory(projectFolder.root).call();
    }

    private void addCommit() {
        new File(projectFolder.root, "build.gradle").append("// addition")
        git.add().addFilepattern("build.gradle").call()
        git.commit().setMessage("addition").call()
    }

    private void addTag(String tagName) {
        git.tag().setName(tagName).call()
    }

    private AndroidGitVersionExtension makePlugin() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(projectFolder.root)
                .build()
        project.pluginManager.apply 'com.gladed.androidgitversion'
        def extension = project.getExtensions().getByName('androidGitVersion')
        assertTrue(extension instanceof AndroidGitVersionExtension)
        return (AndroidGitVersionExtension) extension
    }

    @Override
    protected void setUp() throws Exception {
        projectFolder.create()
        projectFolder.newFile("build.gradle")
//        System.setProperty("user.dir", projectFolder.root.getAbsolutePath())
    }

    @Override
    protected void tearDown() throws Exception {
        projectFolder.delete()
    }
}
