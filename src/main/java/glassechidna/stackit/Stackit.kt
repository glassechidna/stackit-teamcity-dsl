import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script

fun BuildSteps.magicScript(content: String) {
    script {
        scriptContent = content.trimIndent()
//        dockerImage = "mcr.microsoft.com/dotnet/core/sdk:2.1"
        dockerImage = "awsteele/dotnet21:2"
        dockerImagePlatform = ScriptBuildStep.ImagePlatform.Linux
    }
}

fun BuildSteps.assumeRole(roleArn: String, region: String) {
    script {
        scriptContent = """
            set -eu

            credential_file=credential.json

            unset AWS_SESSION_TOKEN
            unset AWS_SECRET_ACCESS_KEY
            unset AWS_ACCESS_KEY_ID

            echo "Assuming the role ..."
            aws sts assume-role --role-arn $roleArn --role-session-name sessionname --duration-seconds 3600 > ${"$"}credential_file

            echo "Exporting environment variables ..."
            export AccessKeyId="${'$'}(cat ${"$"}credential_file | jq -r .Credentials.AccessKeyId)"
            export SecretAccessKey="${'$'}(cat ${"$"}credential_file | jq -r .Credentials.SecretAccessKey)"
            export SessionToken="${'$'}(cat ${"$"}credential_file | jq -r .Credentials.SessionToken)"

            echo "Deleting the credential file"
            rm -rf ${"$"}credential_file

            echo "Setting environment variables..."
            echo "##teamcity[setParameter name='env.AWS_ACCESS_KEY_ID' value='${"$"}{AccessKeyId}']"
            echo "##teamcity[setParameter name='env.AWS_SECRET_ACCESS_KEY' value='${"$"}{SecretAccessKey}']"
            echo "##teamcity[setParameter name='env.AWS_SESSION_TOKEN' value='${"$"}{SessionToken}']"
            echo "##teamcity[setParameter name='env.AWS_REGION' value='$region']"
        """.trimIndent()
    }
}

class StackitPackageParams {
    var stackName = ""
    var templatePath = ""
    var role = ""
    var region = ""
    var params = HashMap<String, String>()

    constructor(init: StackitPackageParams.() -> Unit) {
        init()
    }
}

fun ParametrizedWithType.stackitPackage(p: StackitPackageParams) {
    param("stackName", p.stackName)
    param("templatePath", p.templatePath)
    param("role", p.role)
    param("region", p.region)
    param("cfnParams", p.params.entries.map { kv -> "${kv.key}=${kv.value}" }.joinToString(" "))
}

object StackitPackage : Template({
    uuid = "d449cd37-31a9-4f79-bb2f-daddb26d84b5"
    name = "stackitPackageTemplate"

    params {
        param("stackName", "")
        param("templatePath", "")
        param("role", "")
        param("region", "")
        param("cfnParams", "")
    }

    vcs {
        root(DslContext.settingsRootId)
        cleanCheckout = true
    }

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

    steps {
        assumeRole("%role%", "%region%")
        script {
            scriptContent = """
                set -eux
                stackit package \
                  --stack-name %stackName% \
                  --template %templatePath% \
                  %cfnParams%
            """.trimIndent()
            dockerImage = "awsteele/dotnet21:2"
            dockerImagePlatform = ScriptBuildStep.ImagePlatform.Linux
        }
    }

    artifactRules = "stackit.packaged.yml"
})

fun Dependencies.stackitPackage(b: BuildType) {
    snapshot(b) {}
    artifacts(b) {
        artifactRules = "stackit.packaged.yml"
    }
}

class StackitExecuteParams {
    var stackName = ""
    var role = ""
    var region = ""

    constructor(init: StackitExecuteParams.() -> Unit) {
        init()
    }
}

fun ParametrizedWithType.stackitExecute(p: StackitExecuteParams) {
    param("stackName", p.stackName)
    param("role", p.role)
    param("region", p.region)
}

object StackitExecute : Template({
    uuid = "d8877647-4c31-4f4f-a334-2b05c52f2b74"
    name = "stackitExecuteTemplate"

    params {
        param("stackName", "")
        param("role", "")
        param("region", "")
    }

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

    steps {
        assumeRole("%role%", "%region%")
        script {
            scriptContent = """
                set -eux
                stackit execute --stack-name %stackName%
            """.trimIndent()
            dockerImage = "awsteele/dotnet21:2"
            dockerImagePlatform = ScriptBuildStep.ImagePlatform.Linux
        }
    }
})
