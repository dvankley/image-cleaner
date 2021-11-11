package net.djvk.imageCleaner

import net.djvk.imageCleaner.ui.UiApp
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext


@SpringBootApplication
class ImageCleanerApplication(
    private val context: ConfigurableApplicationContext,

    private val uiApp: UiApp,
) : ApplicationRunner {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun run(args: ApplicationArguments?) {
        if (args == null) {
            throw IllegalArgumentException("No arguments specified, ending run.")
        }

//        val scope = args.getOptionValues("scope")?.get(0)?.let { ScrapingScope.getOrNull(it.toUpperCase()) }
//            ?: ScrapingScope.ALL

//        logger.info("Scope: $scope")

        uiApp.show()

        context.close()
    }
}

fun main(args: Array<String>) {
    SpringApplicationBuilder(ImageCleanerApplication::class.java).headless(false).run(*args)
}
