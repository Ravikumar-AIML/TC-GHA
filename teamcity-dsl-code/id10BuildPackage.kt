package _Self.buildTypes

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.PowerShellStep
import jetbrains.buildServer.configs.kotlin.buildSteps.exec
import jetbrains.buildServer.configs.kotlin.buildSteps.powerShell
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

object id10BuildPackage : BuildType({
    templates(AbsoluteId("GitHubPullRequestTrigger"), AbsoluteId("InstallHaloGcveClient"))
    id("10BuildPackage")
    name = "1.0 - Build UltiPro package"

    artifactRules = """
        %artifacts_path%\*.*
        %artifacts_path%\Logs\*.* => BuilderLogs.zip
        %repoDir%\owner_changelist.json
    """.trimIndent()
    buildNumberPattern = "%system.build.version%.%Build.DataVersion%"

    params {
        param("Release.Name", "UltiPro %version% - 2020 R1 Release")
        param("MinimumClientVersion", "23813")
        param("Baseline.Build", "%system.Baseline.DataVersion%")
        param("PatchSwitch", "")
        param("Baseline.Tag", "9b9872cd1c8bde34fcbf0dd0f4b86f533102c04b")
        param("name", "")
        param("Build.DataVersion", "%build.counter%")
        param("Baseline.ArtifactsPath", """%unc.groups%\Utilities\BCP_Baseline\""")
        param("environment.type", "sql")
        param("temp.bdlib.buildnumber", "1228")
        param("queue.name", "")
        param("Release.Version", "%version%")
    }

    vcs {
        root(AbsoluteId("CuUKGProCoreNew"), "+:Metadata/UltiPro")

        cleanCheckout = true
        excludeDefaultBranchChanges = true
    }

    steps {
        powerShell {
            name = "Fetch tags"
            id = "RUNNER_1235"
            edition = PowerShellStep.Edition.Core
            scriptMode = script {
                content = """
                    #${'$'}env:GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=no"
                    git fetch origin +refs/tags/*:refs/tags/*
                """.trimIndent()
            }
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        powerShell {
            name = "Setup parameters"
            id = "RUNNER_2077"
            edition = PowerShellStep.Edition.Core
            workingDir = "%repoDir%"
            scriptMode = script {
                content = """
                    ${'$'}Product = '%product%'
                    ${'$'}Branch = if ('%teamcity.pullRequest.target.branch%' -and ('%teamcity.pullRequest.target.branch%' -ne '%%teamcity.pullRequest.target.branch%%') ) { '%teamcity.pullRequest.target.branch%' } else { '%teamcity.build.branch%' }
                    ${'$'}EnvironmentType = '%environment.type%'
                    ${'$'}Credential = New-Object -TypeName pscredential -ArgumentList @(
                        '%bnd.tc.svc.user%'
                        ConvertTo-SecureString -String '%bnd.tc.svc.pass%' -AsPlainText -Force
                    )
                    ${'$'}Path = '%repoDir%'
                    
                    Write-Host "Branch: ${'$'}Branch %%teamcity.pullRequest.target.branch%%"
                    
                    git config merge.renamelimit 5000000
                    git config --get-all merge.renamelimit
                    
                    & ./ps/build.ps1 -ErrorAction Stop -tasks validate_audit_configuration, build_package -taskfile pipelines/uds/metadata -parameters @{
                        ReleaseMetadata     = Get-Content -Path '.\uds-metadata.json' | ConvertFrom-Json
                        Product             = ${'$'}Product
                        Branch              = ${'$'}Branch
                        BitBucketCredential = ${'$'}Credential
                        Path                = ${'$'}Path
                        Override            = @{ 'validate_parent_commit' = ${'$'}true }
                    
                        ParameterMapping    = @{
                            'build.number'                = { '{0}.{1}' -f ${'$'}Release.publicVersion, '%build.counter%' }
                            'Baseline.Tag'                = {
                                if (${'$'}Branch -match '%branch.pattern%' -or (${'$'}Metadata.ResolvedBranch -and ${'$'}Metadata.ResolvedBranch -match '%branch.pattern%')) {
                                    ${'$'}Release.BaselineTag
                                } else {
                                    "begin-${'$'}(([version]${'$'}Release.publicVersion).Major)"
                                }
                            }
                            'system.Baseline.DataVersion' = { ${'$'}Release.BaselineProductVersion }
                            'Release.Name'                = { ${'$'}Release.name }
                            'MinimumClientVersion'        = { ${'$'}Release.clientversion }
                            'queue.name' = {
                                ${'$'}name = ${'$'}Release.queues.${'$'}EnvironmentType
                                if (${'$'}env:RC_osname -eq 'win2019all') { "gq9" } else { ${'$'}name }
                            }
                            'Release.Version'             = { 
                                if (${'$'}Branch -match '%branch.pattern%' -or (${'$'}Metadata.ResolvedBranch -and ${'$'}Metadata.ResolvedBranch -match '%branch.pattern%')) {
                                    ${'$'}Release.TargetRelease
                                } else {
                                    ${'$'}Release.publicVersion
                                }
                            }
                            'PatchSwitch'                 = {
                                if (${'$'}Branch -match '%branch.pattern%' -or (${'$'}Metadata.Resolvedbranch -and ${'$'}Metadata.Resolvedbranch -match '%branch.pattern%')) {
                                    '/IsPatch'
                                }
                            }
                            'Baseline.ArtifactsPath'      = {
                                Join-Path -Path '%Baseline.ArtifactsPath%' -ChildPath ( '{0}.0.0' -f ( ( [version]${'$'}Release.publicVersion ).Major - 1 ) ) |
                                Join-Path -ChildPath ${'$'}Product
                            }
                        }
                    }
                """.trimIndent()
            }
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        powerShell {
            name = "Deploy from queue"
            id = "RUNNER_2911"
            edition = PowerShellStep.Edition.Core
            scriptMode = script {
                content = """
                    ${'$'}isPatch = [string]::IsNullOrEmpty('%PatchSwitch%') -eq ${'$'}false
                    ${'$'}hasEnv = [string]::IsNullOrEmpty('%name%') -eq ${'$'}false
                    ${'$'}isRelease = ${'$'}isPatch -eq ${'$'}false
                    ${'$'}isEmptyEnv = ${'$'}hasEnv -eq ${'$'}false
                    switch (${'$'}true) {
                        { ${'$'}isPatch -and ${'$'}hasEnv } { "##teamcity[setParameter name='name' value='']"; break }
                        { ${'$'}isRelease -and ${'$'}isEmptyEnv } { rc dequeue '%queue.name%' -async; break }
                    }
                """.trimIndent()
            }
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        powerShell {
            name = "Validate environment was obtained"
            id = "RUNNER_2919"
            scriptMode = script {
                content = """
                    ${'$'}isPatch = [string]::IsNullOrEmpty('%PatchSwitch%') -eq ${'$'}false
                    ${'$'}isRelease = ${'$'}isPatch -eq ${'$'}false
                    if(${'$'}isRelease){
                        if([string]::IsNullOrWhiteSpace("%name%")){
                            "##teamcity[buildProblem description='Could not obtain an environment from the queue']"
                        }
                    }
                """.trimIndent()
            }
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        powerShell {
            name = "Validate environment is not in a failed state"
            id = "RUNNER_2925"
            scriptMode = script {
                content = """
                    ${'$'}haloEndPoint = '%env.RC_api_url%'
                    ${'$'}envName = '%name%'
                    if(${'$'}(Invoke-RestMethod -Uri "${'$'}haloEndPoint/env/${'$'}envName.json" -ErrorAction Stop).failed -eq 'true'){
                        "##teamcity[buildProblem description='Environment pulled from queue was in a failed state']"
                    }
                """.trimIndent()
            }
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        powerShell {
            name = "Get differences"
            id = "RUNNER_1499"
            edition = PowerShellStep.Edition.Core
            workingDir = "%repoDir%"
            scriptMode = script {
                content = """
                    #${'$'}env:GIT_SSH_COMMAND = "ssh -o StrictHostKeyChecking=no"
                    'git diff %Baseline.Tag%..HEAD --name-only --pretty="format:" | %{if(${'$'}_ -match ''^Metadata/%product%''){${'$'}_ -replace ''Metadata/%product%/''}} | Sort-Object -Unique'
                    git diff %Baseline.Tag%..HEAD --name-only --pretty="format:" | %{if(${'$'}_ -match '^Metadata/%product%'){${'$'}_ -replace 'Metadata/%product%/'}} | Sort-Object -Unique > .artifacts/changedfiles.txt
                """.trimIndent()
            }
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        exec {
            name = "Build updater package"
            id = "RUNNER_1484"
            workingDir = "%artifacts_path%"
            path = "SUBuilder.exe"
            arguments = """/ZipFile=ServerUpdater.Package.zip /Path=%repoDir% /Build=%Build.DataVersion% /BaselineBuild=%system.Baseline.DataVersion% "/ReleaseName=%Release.Name%" /ExtraVersions=HRMSClientBuild\%MinimumClientVersion%;ReleaseVersion\%Release.Version% /UpdaterCLR=%CLRDir% /LogPath=%logPath% /WorkPath=%workPath% /ChangeList=changedfiles.txt /Type=Patch "/Branch=%teamcity.build.branch%" /BaselineArtifactsPath=%Baseline.ArtifactsPath% /Server=%name%db /User=%environment.user% /Password=%environment.password%"""
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Create SFX"
            id = "RUNNER_1714"
            workingDir = "%repoDir%"
            scriptContent = """
                xcopy %artifacts_path%\ServerUpdater.Package.zip %package_path% /Y
                xcopy %artifacts_path%\changedfiles.txt %package_path% /Y
                xcopy %repoDir%\owner_changelist.json %package_path% /Y
                SfxStub.exe .\package ServerUpdater.exe ServerUpdater_Ultipro.exe
                xcopy ServerUpdater_Ultipro.exe %artifacts_path% /Y
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        powerShell {
            name = "Clean up environment"
            id = "RUNNER_2367"
            scriptMode = script {
                content = """
                    if ([string]::IsNullOrEmpty('%PatchSwitch%')) {
                        rc delete %name% -owner %queue.name%_q
                    }
                """.trimIndent()
            }
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
    }

    triggers {
        vcs {
            id = "vcsTrigger"
            enabled = false
        }
    }

    failureConditions {
        executionTimeoutMin = 60
    }

    features {
        sshAgent {
            id = "ssh-agent-build-feature"
            teamcitySshKey = "svcgitbnd@ukg.com"
        }
    }

    dependencies {
        artifacts(AbsoluteId("BadTools_Poc_ServerUpdater_30Promote_30Promote")) {
            id = "ARTIFACT_DEPENDENCY_555"
            buildRule = lastPinned()
            artifactRules = """
                ServerUpdater.zip!/ServerUpdater.exe => %artifacts_path%
                ServerUpdater.zip!/Newtonsoft.Json.dll => %artifacts_path%
                ServerUpdaterCLR.zip!/ServerUpdaterCLR.dll => %artifacts_path%
                Administration.zip => %artifacts_path%
                SUBuilder.zip!** => %artifacts_path%
                ServerUpdater.zip!/ServerUpdater.exe => %package_path%
                ServerUpdater.zip!/Newtonsoft.Json.dll => %package_path%
                ServerUpdaterCLR.zip!/ServerUpdaterCLR.dll => %package_path%
            """.trimIndent()
        }
        artifacts(AbsoluteId("SfxStub_03Promote")) {
            id = "ARTIFACT_DEPENDENCY_1032"
            buildRule = lastPinned()
            artifactRules = "SfxStub.exe => %repoDir%"
        }
        artifacts(AbsoluteId("UltiPro_DevSonata_0Build_DbMetadata_Ultipro_00Maintenance_PublishMetadata")) {
            id = "ARTIFACT_DEPENDENCY_1316"
            buildRule = lastSuccessful()
            artifactRules = "uds-metadata.json => %repoDir%"
        }
        artifacts(AbsoluteId("bdlib_30Promote")) {
            id = "ARTIFACT_DEPENDENCY_1315"
            buildRule = lastSuccessful()
            artifactRules = "bdlib.zip!** => %repoDir%"
        }
    }

    cleanup {
        baseRule {
            artifacts(builds = 5, artifactPatterns = """
                +:**/*
                -:.teamcity/properties/*.gz
            """.trimIndent())
        }
    }
    
    disableSettings("RUNNER_3489")
})
