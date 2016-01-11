package com.gladed.gradle.androidgitversion

import org.eclipse.jgit.api.Git
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.rules.TemporaryFolder

class AndroidGitVersionTest extends GroovyTestCase {

    def projectFolder = new TemporaryFolder()
    File projectDir

    public void testNoGitRepo() {
        def plugin = getExtension();
        assertEquals('unknown', plugin.name())
    }

    public void testNoTag() {
        Git git = Git.init().setDirectory(projectDir).call();
        def plugin = getExtension();
        assertEquals('unknown', plugin.name())
    }

    public void testTag() {
        Git git = Git.init().setDirectory(projectDir).call()
        git.add().addFilepattern("build.gradle").call()
        git.commit().setMessage("first").call()
        git.tag().setName("1.0").call()
        def plugin = getExtension();
        assertEquals('1.0', plugin.name())
    }

    private void execute(String command) {
        command.execute([], projectDir)
    }

    private AndroidGitVersionExtension getExtension() {
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
        projectDir = projectFolder.root
        projectFolder.newFile("build.gradle")
        System.setProperty("user.dir", projectDir.getAbsolutePath())
        println('*** using ' + projectDir)
    }
}
