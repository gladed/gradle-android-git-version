package com.gladed.androidgitversion

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.revwalk.RevCommit

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.rules.TemporaryFolder

abstract class AndroidGitVersionTest extends GroovyTestCase {

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

    RevCommit addCommit() {
        new File(projectFolder.root, "build.gradle").append("// addition")
        git.add().addFilepattern("build.gradle").call()
        git.commit().setMessage("addition").call()
    }

    void addTag(String tagName) {
        git.tag().setName(tagName).call()
    }

    void addLightweightTag(String tagName) {
        git.tag().setName(tagName).setAnnotated(false).call()
    }

    void addBranch(String branchName) {
        git.checkout().setCreateBranch(true).setName(branchName).call()
    }

    void checkout(String branchName) {
        git.checkout().setName(branchName).call()
    }

    void merge(AnyObjectId from) {
        git.merge().setCommit(true).include(from).call()
    }

    @Override
    protected void tearDown() throws Exception {
        projectFolder.delete()
    }
}
