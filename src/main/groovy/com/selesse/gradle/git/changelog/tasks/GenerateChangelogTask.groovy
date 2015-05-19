package com.selesse.gradle.git.changelog.tasks
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.selesse.gradle.git.GitCommandExecutor
import com.selesse.gradle.git.changelog.GitChangelogExtension
import com.selesse.gradle.git.changelog.generator.ChangelogGenerator
import com.selesse.gradle.git.changelog.generator.ComplexChangelogGenerator
import com.selesse.gradle.git.changelog.generator.SimpleChangelogGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskAction

class GenerateChangelogTask extends DefaultTask {
    Logger logger = Logging.getLogger(GenerateChangelogTask)

    public GenerateChangelogTask() {
        this.description = 'Generates a changelog'
        this.group = 'build'
    }

    @TaskAction
    def generateChangelog() {
        def outputDirectoryFile = project.changelog.outputDirectory as File
        def fileName = project.changelog.fileName as String
        File changelogFile = new File(outputDirectoryFile, fileName)
        outputDirectoryFile.mkdirs()

        generateChangelog(new PrintStream(new FileOutputStream(changelogFile)))
    }

    def generateChangelog(PrintStream printStream) {
        String content = generateChangelogContent()

        printStream.print(content)
        printStream.flush()

        return content
    }

    String generateChangelogContent() {
        GitChangelogExtension extension = project.changelog

        def title = extension.title
        def heading = "$title\n${'='.multiply(title.length())}\n\n"

        def changelogFormat = extension.commitFormat
        def gitCommandExecutor = new GitCommandExecutor(changelogFormat)

        return heading + generateChangelogContent(extension, gitCommandExecutor)
    }

    String generateChangelogContent(GitChangelogExtension extension, GitCommandExecutor gitExecutor) {
        List<String> tags = getTagList(extension, gitExecutor)

        ChangelogGenerator changelogGenerator

        if (tags.size() == 0) {
            logger.info("No tags found, generating basic changelog")
            changelogGenerator = new SimpleChangelogGenerator(gitExecutor)
        } else {
            logger.info("{} tags were found, generating complex changelog", tags.size())
            changelogGenerator = new ComplexChangelogGenerator(gitExecutor, tags,
                    !['last_tag', 'beginning'].contains(extension.since))
        }

        def changelog = changelogGenerator.generateChangelog()
        changelog = filterOrProcessLinesIfSet(extension, changelog)
        return changelog
    }

    private String filterOrProcessLinesIfSet(GitChangelogExtension extension, String changelog) {
        if (extension.includeLines || extension.processLines) {
            Iterable<String> changelogLines = Splitter.on('\n').split(changelog)
            if (extension.includeLines) {
                changelogLines = changelogLines.findAll extension.includeLines
            }
            if (extension.processLines) {
                changelogLines = changelogLines.collect extension.processLines
            }
            changelog = Joiner.on('\n').join(changelogLines)
        }
        changelog
    }

    private List<String> getTagList(GitChangelogExtension extension, GitCommandExecutor gitExecutor) {
        def tags
        if (extension.since == 'last_tag') {
            def lastTag = gitExecutor.getLastTag()
            if (lastTag.isEmpty()) {
                throw new GradleException('"last_tag" option specified, but no tags were found')
            }
            tags = [lastTag]
        } else if (extension.since == 'beginning') {
            tags = gitExecutor.getTags()
        } else {
            tags = gitExecutor.getTagsSince(extension.since)
            if (tags.isEmpty()) {
                throw new GradleException("No tags found since '${extension.since}'")
            }
        }
        tags
    }
}
