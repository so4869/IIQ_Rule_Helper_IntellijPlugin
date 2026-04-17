package im.flare

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyPlugin : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Plugin startup logic
    }
}
