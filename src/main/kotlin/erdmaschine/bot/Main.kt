package erdmaschine.bot

import erdmaschine.bot.listener.CommandListener
import erdmaschine.bot.listener.MessageListener
import erdmaschine.bot.listener.ReactionListener
import erdmaschine.bot.model.DbStorage
import erdmaschine.bot.model.MemoryStorage
import erdmaschine.bot.reddit.RedditFacade
import erdmaschine.bot.reddit.RedditIntegrationRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import org.slf4j.LoggerFactory
import kotlin.time.ExperimentalTime

@ExperimentalTime
fun main() = runBlocking {
    val env = Env()
    val log = LoggerFactory.getLogger("erdmaschine.bot.Main")!!
    var redditIntegrationJob: Job? = null

    try {
        log.info("Starting up erdmaschine-bot")

        val storage = if (env.dbUrl.isNotBlank()) {
            DbStorage(env)
        } else {
            log.info("No jdbc url found, using memory storage")
            MemoryStorage()
        }

        val redditFacade = RedditFacade(env)
        val redditIntegrationRunner = RedditIntegrationRunner(env, storage, redditFacade)
        redditIntegrationJob = redditIntegrationRunner.run()

        val commandListener = CommandListener(storage)

        val jda = JDABuilder.createDefault(env.authToken)
            .addEventListeners(
                ReactionListener(storage),
                MessageListener(storage),
                commandListener
            )
            .build()

        jda.awaitReady()
        commandListener.initCommands(jda, env)
        jda.presence.setPresence(Activity.watching("out for hot takes"), false)
    } catch (e: Exception) {
        redditIntegrationJob?.cancel()
        log.error(e.message, e)
    }
}
