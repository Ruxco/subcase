import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository

buildscript {
    dependencies {
        classpath(libs.org.eclipse.jgit)
    }
}

val repo = FileRepository(rootProject.file(".git"))
val refId = repo.resolve("HEAD")!!
val commitCount = Git(repo).log().add(refId).call().count()

val verCode by extra(commitCount)
// 发布 tag（v1.2.3）构建时自动用 tag 作为版本名；本地/分支构建回退到 1.3.1。
val verName by extra(
    System.getenv("GITHUB_REF_NAME")
        ?.takeIf { it.startsWith("v") }
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() }
        ?: "1.3.1"
)

val androidTargetSdkVersion by extra(34)
val androidCompileSdkVersion by extra(34)

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.compose.compiler) apply false
}
