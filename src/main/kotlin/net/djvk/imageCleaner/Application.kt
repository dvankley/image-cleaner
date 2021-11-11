package net.djvk.imageCleaner

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ConfigurableApplicationContext

@SpringBootApplication
class Application(
    private val context: ConfigurableApplicationContext,
) : ApplicationRunner {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun run(args: ApplicationArguments?) {
        if (args == null) {
            throw IllegalArgumentException("No arguments specified, ending run.")
        }

//        val scope = args.getOptionValues("scope")?.get(0)?.let { ScrapingScope.getOrNull(it.toUpperCase()) }
//            ?: ScrapingScope.ALL

//        logger.info("Scope: $scope")


        context.close()
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
