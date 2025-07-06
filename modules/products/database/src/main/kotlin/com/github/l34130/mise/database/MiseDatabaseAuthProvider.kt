package com.github.l34130.mise.database

import com.github.l34130.mise.core.MiseHelper
import com.github.l34130.mise.core.setting.MiseProjectSettings
import com.intellij.credentialStore.Credentials
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseAuthProvider
import com.intellij.database.dataSource.DatabaseConnectionConfig
import com.intellij.database.dataSource.DatabaseConnectionInterceptor
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.DatabaseCredentialsAuthProvider
import com.intellij.database.dataSource.url.template.MutableParametersHolder
import com.intellij.database.dataSource.url.template.ParametersHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

class MiseDatabaseAuthProvider : DatabaseAuthProvider {
    override fun getId(): @NonNls String = "mise"

    override fun getDisplayName(): @Nls String = "Mise"

    override suspend fun interceptConnection(
        proto: DatabaseConnectionInterceptor.ProtoConnection,
        silent: Boolean,
    ): Boolean {
        val project = proto.project
        val configEnvironment = project.service<MiseProjectSettings>().state.miseConfigEnvironment
        val miseEnvVars =
            MiseHelper.getMiseEnvVarsOrNotify(
                project = project,
                configEnvironment = configEnvironment,
            )
        val loadEnvVar = { key: String ->
            if (key.startsWith("$")) {
                miseEnvVars[key.substring(1)] ?: throw IllegalArgumentException("Mise environment variable '$key' not found")
            } else {
                key
            }
        }
        val username =
            proto.connectionPoint.getAdditionalProperty(PROP_USERNAME)?.let { loadEnvVar(it) }
        val password =
            proto.connectionPoint.getAdditionalProperty(PROP_PASSWORD)?.let { loadEnvVar(it) }

        DatabaseCredentialsAuthProvider.applyCredentials(proto, Credentials(username, password), true)

        return true
    }

    override fun createWidget(
        project: Project?,
        credentials: DatabaseCredentials,
        config: DatabaseConnectionConfig,
    ): DatabaseAuthProvider.AuthWidget = MiseAuthWidget()

    companion object {
        const val PROP_USERNAME = "mise.username"
        const val PROP_PASSWORD = "mise.password"
    }

    private class MiseAuthWidget : DatabaseAuthProvider.AuthWidget {
        private val myUsernameTf = JBTextField()
        private val myPasswordTf = JBTextField()
        private val component =
            panel {
                row("Username:") {
                    cell(myUsernameTf)
                }
                row("Password:") {
                    cell(myPasswordTf)
                }
            }

        override fun getComponent(): JComponent = component

        override fun getPreferredFocusedComponent(): JComponent = myUsernameTf

        override fun save(
            config: DatabaseConnectionConfig,
            copyCredentials: Boolean,
        ) {
            config.setAdditionalProperty(PROP_USERNAME, myUsernameTf.text)
            config.setAdditionalProperty(PROP_PASSWORD, myPasswordTf.text)
        }

        override fun reset(
            point: DatabaseConnectionPoint,
            resetCredentials: Boolean,
        ) {
            point.getAdditionalProperty(PROP_USERNAME)?.let { myUsernameTf.text = it }
            point.getAdditionalProperty(PROP_PASSWORD)?.let { myPasswordTf.text = it }
        }

        override fun onChanged(r: Runnable) { }

        override fun isPasswordChanged(): Boolean = false

        override fun hidePassword() { }

        override fun reloadCredentials() { }

        override fun forceSave() { }

        override fun updateFromUrl(holder: ParametersHolder) { }

        override fun updateUrl(model: MutableParametersHolder) { }
    }
}
