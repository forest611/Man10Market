package red.man10.man10market.assistant

import org.bukkit.entity.Player
import red.man10.man10itembank.util.MySQLManager
import red.man10.man10market.Man10Market
import java.util.*
import java.util.concurrent.ConcurrentHashMap
// OpenAIライブラリの参照を削除

/**
 * プレイヤーごとの会話履歴を管理するクラス
 */
class ConversationManager private constructor() {
    private val conversationCache = ConcurrentHashMap<UUID, MutableList<Conversation>>()
    private lateinit var plugin: Man10Market
    private lateinit var mysql: MySQLManager

    companion object {
        private var instance: ConversationManager? = null
        private const val MAX_CONVERSATION_HISTORY = 10 // 保持する会話履歴の最大数

        fun getInstance(): ConversationManager {
            return instance ?: synchronized(this) {
                instance ?: ConversationManager().also { instance = it }
            }
        }

        fun setup(plugin: Man10Market) {
            this.instance = ConversationManager()
            this.instance!!.initialize(plugin)
        }
    }

    /**
     * 初期化処理
     */
    fun initialize(plugin: Man10Market) {
        this.plugin = plugin
        this.mysql = MySQLManager(plugin, "AssistantConversation")
    }

    /**
     * プレイヤーの会話履歴をキャッシュから取得
     * キャッシュにない場合はDBから読み込む
     */
    fun getConversationHistory(player: Player): List<Conversation> {
        val uuid = player.uniqueId
        
        if (!conversationCache.containsKey(uuid)) {
            loadConversationHistory(player)
        }
        
        return conversationCache[uuid] ?: emptyList()
    }

    /**
     * DBからプレイヤーの会話履歴を読み込む
     */
    private fun loadConversationHistory(player: Player) {
        val uuid = player.uniqueId
        val history = mutableListOf<Conversation>()
        
        val query = "SELECT message, response, created_at FROM assistant_conversation WHERE uuid = '${uuid}' ORDER BY created_at DESC LIMIT $MAX_CONVERSATION_HISTORY"
        mysql.query(query)?.use { rs ->
            while (rs.next()) {
                val message = rs.getString("message")
                val response = rs.getString("response")
                val createdAt = rs.getTimestamp("created_at")
                
                history.add(Conversation(message, response, createdAt))
            }
        }
        
        conversationCache[uuid] = history
    }

    /**
     * 会話履歴をDBに保存し、キャッシュを更新
     */
    fun saveConversation(player: Player, message: String, response: String) {
        val uuid = player.uniqueId
        val playerName = player.name
        
        // DBに保存
        val query = "INSERT INTO assistant_conversation (uuid, player, message, response) VALUES ('${uuid}', '${playerName}', '${message.replace("'", "''")}', '${response.replace("'", "''")}')" 
        mysql.execute(query)
        
        // キャッシュを更新
        val history = conversationCache.getOrPut(uuid) { mutableListOf() }
        history.add(0, Conversation(message, response, Date()))
        
        // 最大数を超えた場合、古いものを削除
        if (history.size > MAX_CONVERSATION_HISTORY) {
            history.removeAt(history.size - 1)
        }
    }

    /**
     * プレイヤーの会話履歴をクリア
     */
    fun clearConversationHistory(player: Player) {
        val uuid = player.uniqueId
        
        // DBから削除
        mysql.execute("DELETE FROM assistant_conversation WHERE uuid = '${uuid}'")
        
        // キャッシュから削除
        conversationCache.remove(uuid)
    }

    /**
     * 会話履歴をプロンプト用の文字列に変換 (非推奨: 後方互換性のために残しています)
     */
    fun getConversationHistoryAsPrompt(player: Player): String {
        val history = getConversationHistory(player)
        if (history.isEmpty()) return ""
        
        val sb = StringBuilder()
        sb.append("以下は過去の会話履歴です：\n")
        
        // 新しい順に取得されるので、古い順に並べ替え
        history.reversed().forEach { conversation ->
            sb.append("ユーザー: ${conversation.message}\n")
            sb.append("アシスタント: ${conversation.response}\n\n")
        }
        
        return sb.toString()
    }
    
    // getConversationHistoryAsMessagesメソッドは不要になったので削除
}

/**
 * 会話履歴を表すデータクラス
 */
data class Conversation(
    val message: String,
    val response: String,
    val createdAt: Date
)
