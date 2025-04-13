package red.man10.man10market.assistant

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * サブタスクの種類を表す列挙型
 */
enum class TaskType {
    @SerializedName("info_gathering")
    INFO_GATHERING,    // 情報収集
    
    @SerializedName("condition_check")
    CONDITION_CHECK,   // 条件チェック
    
    @SerializedName("trade_execution")
    TRADE_EXECUTION,   // 取引実行
    
    @SerializedName("result_report")
    RESULT_REPORT      // 結果レポート
}

/**
 * サブタスクを表すデータクラス
 */
data class SubTask(
    val type: TaskType,
    val description: String,
    val parameters: Map<String, Any> = mapOf()
)

/**
 * サブタスク実行の結果を表すデータクラス
 */
data class TaskResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = mapOf()
)

/**
 * AIから返されるタスクリスト
 */
data class TaskList(
    val tasks: List<TaskInfo>
) {
    companion object {
        private val gson = Gson()
        
        /**
         * JSON文字列からTaskListを生成
         */
        fun fromJson(json: String): TaskList? {
            return try {
                gson.fromJson(json, TaskList::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 個々のタスク情報
 */
data class TaskInfo(
    val task: String,
    val type: TaskType? = null,
    val parameters: Map<String, Any> = mapOf()
) {
    /**
     * TaskInfoをSubTaskに変換
     */
    fun toSubTask(): SubTask {
        return SubTask(
            type = type ?: TaskType.INFO_GATHERING,
            description = task,
            parameters = parameters
        )
    }
}
