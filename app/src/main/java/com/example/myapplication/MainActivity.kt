package com.example.myfirstapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            STEMLearnTheme {
                StemLearningApp()
            }
        }
    }
}

// Subject Enum
enum class Subject(val stringValue: String) {
    PLANT("plant"),
    ANIMAL("animal"),
    PHYSICS("physics"),
    UNKNOWN("unknown");

    companion object {
        fun fromString(value: String?): Subject {
            return try {
                entries.find { it.stringValue.equals(value, ignoreCase = true) } ?: UNKNOWN
            } catch (e: Exception) {
                Log.e("Subject", "Error parsing subject: $value", e)
                UNKNOWN
            }
        }
    }
}

// Study Data Classes
data class StudyTopic(
    val title: String,
    val icon: String,
    val concepts: List<StudyConcept>
)

data class StudyConcept(
    val title: String,
    val definition: String,
    val keyPoints: List<String>,
    val examples: List<String>,
    val interactiveElement: InteractiveElement? = null,
    val funFacts: List<String> = emptyList()
)

sealed class InteractiveElement {
    data class ImageClick(val parts: List<ClickablePart>) : InteractiveElement()
    data class DragDrop(val items: List<String>, val categories: List<String>) : InteractiveElement()
    data class Timeline(val events: List<TimelineEvent>) : InteractiveElement()
    data class Comparison(val items: Map<String, List<String>>) : InteractiveElement()
}

data class ClickablePart(val name: String, val x: Float, val y: Float, val description: String)
data class TimelineEvent(val step: String, val description: String, val detail: String)

// Game Data Classes
enum class GameType { MCQ, LABELING, MATCHING, MINDMAP }
data class GameQuestion(
    val question: String,
    val type: GameType,
    val options: List<String> = emptyList(),
    val correctAnswer: String = "",
    val labelingData: LabelingGameData? = null,
    val matchingData: MatchingGameData? = null,
    val mindMapData: MindMapData? = null
)
data class LabelingGameData(val imageParts: List<PlantPart>, val labels: List<String>)
data class PlantPart(val name: String, val x: Float, val y: Float)
data class MatchingGameData(val leftItems: List<String>, val rightItems: List<String>, val correctPairs: Map<String, String>)
data class MindMapData(val centralTopic: String, val branches: List<MindMapBranch>)
data class MindMapBranch(val title: String, val items: List<String>, val color: Color)

// Study ViewModel
class StudyViewModel(private val grade: Int, private val subject: Subject) : ViewModel() {
    private val _studyTopics = MutableStateFlow<List<StudyTopic>>(emptyList())
    val studyTopics: StateFlow<List<StudyTopic>> = _studyTopics.asStateFlow()

    private val _currentTopicIndex = MutableStateFlow(0)
    val currentTopicIndex: StateFlow<Int> = _currentTopicIndex.asStateFlow()

    private val _currentConceptIndex = MutableStateFlow(0)
    val currentConceptIndex: StateFlow<Int> = _currentConceptIndex.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStudyContent()
    }

    private fun loadStudyContent() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val topics = when (subject) {
                    Subject.PLANT -> getPlantStudyTopics(grade)
                    Subject.ANIMAL -> getAnimalStudyTopics(grade)
                    Subject.PHYSICS -> getPhysicsStudyTopics(grade)
                    Subject.UNKNOWN -> emptyList<StudyTopic>()
                }
                _studyTopics.value = topics
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun nextConcept() {
        val topics = _studyTopics.value
        val currentTopic = _currentTopicIndex.value
        val currentConcept = _currentConceptIndex.value

        if (currentTopic < topics.size) {
            val topic = topics[currentTopic]
            if (currentConcept < topic.concepts.size - 1) {
                _currentConceptIndex.value = currentConcept + 1
            } else if (currentTopic < topics.size - 1) {
                _currentTopicIndex.value = currentTopic + 1
                _currentConceptIndex.value = 0
            }
        }
    }

    fun previousConcept() {
        val currentTopic = _currentTopicIndex.value
        val currentConcept = _currentConceptIndex.value

        if (currentConcept > 0) {
            _currentConceptIndex.value = currentConcept - 1
        } else if (currentTopic > 0) {
            val topics = _studyTopics.value
            _currentTopicIndex.value = currentTopic - 1
            _currentConceptIndex.value = topics[currentTopic - 1].concepts.size - 1
        }
    }

    fun jumpToTopic(topicIndex: Int) {
        _currentTopicIndex.value = topicIndex
        _currentConceptIndex.value = 0
    }
}

class StudyViewModelFactory(private val grade: Int, private val subject: Subject) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return StudyViewModel(grade, subject) as T
    }
}

// Game ViewModel
class GameViewModelFactory(private val grade: Int, private val subject: Subject) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return try {
            if (modelClass.isAssignableFrom(EnhancedGameViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                EnhancedGameViewModel(grade, subject) as T
            } else {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        } catch (e: Exception) {
            Log.e("GameViewModelFactory", "Error creating ViewModel", e)
            throw e
        }
    }
}

class EnhancedGameViewModel(grade: Int, private val subject: Subject) : ViewModel() {
    private val _questions = MutableStateFlow<List<GameQuestion>>(emptyList())
    val questions: StateFlow<List<GameQuestion>> = _questions.asStateFlow()
    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex: StateFlow<Int> = _currentQuestionIndex.asStateFlow()
    private val _currentDisplayQuestion = MutableStateFlow<GameQuestion?>(null)
    val currentDisplayQuestion: StateFlow<GameQuestion?> = _currentDisplayQuestion.asStateFlow()
    private val _currentScore = MutableStateFlow(0)
    val currentScore: StateFlow<Int> = _currentScore.asStateFlow()
    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption: StateFlow<String?> = _selectedOption.asStateFlow()
    private val _showResult = MutableStateFlow(false)
    val showResult: StateFlow<Boolean> = _showResult.asStateFlow()
    private val _isAnswerCorrect = MutableStateFlow<Boolean?>(null)
    val isAnswerCorrect: StateFlow<Boolean?> = _isAnswerCorrect.asStateFlow()
    private val _gameFinishedEvent = MutableSharedFlow<Unit>()
    val gameFinishedEvent = _gameFinishedEvent.asSharedFlow()

    // Error handling state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _labeledParts = MutableStateFlow<Map<String, String>>(emptyMap())
    val labeledParts: StateFlow<Map<String, String>> = _labeledParts.asStateFlow()
    private val _selectedLabel = MutableStateFlow<String?>(null)
    val selectedLabel: StateFlow<String?> = _selectedLabel.asStateFlow()

    private val _matchedPairs = MutableStateFlow<Map<String, String>>(emptyMap())
    val matchedPairs: StateFlow<Map<String, String>> = _matchedPairs.asStateFlow()
    private val _selectedLeftItem = MutableStateFlow<String?>(null)
    val selectedLeftItem: StateFlow<String?> = _selectedLeftItem.asStateFlow()

    init {
        loadQuestions(grade)
    }

    private fun loadQuestions(grade: Int) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val questionList = when (subject) {
                    Subject.PLANT -> getEnhancedPlantQuestions(grade)
                    Subject.ANIMAL -> getEnhancedAnimalQuestions(grade)
                    Subject.PHYSICS -> getEnhancedPhysicsQuestions(grade)
                    Subject.UNKNOWN -> {
                        Log.w("EnhancedGameViewModel", "Unknown subject: $subject. Loading empty question list.")
                        emptyList<GameQuestion>()
                    }
                }

                if (questionList.isEmpty()) {
                    _errorMessage.value = "No questions available for this grade and subject."
                    return@launch
                }

                _questions.value = questionList
                updateCurrentDisplayQuestion()
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e("EnhancedGameViewModel", "Error loading questions", e)
                _errorMessage.value = "Failed to load questions: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateCurrentDisplayQuestion() {
        try {
            val questionList = _questions.value
            val currentIndex = _currentQuestionIndex.value

            if (questionList.isEmpty()) {
                _currentDisplayQuestion.value = null
                _errorMessage.value = "No questions available"
                return
            }

            if (currentIndex < 0 || currentIndex >= questionList.size) {
                Log.w("EnhancedGameViewModel", "Invalid question index: $currentIndex, size: ${questionList.size}")
                _currentDisplayQuestion.value = null
                return
            }

            _currentDisplayQuestion.value = questionList[currentIndex]
        } catch (e: Exception) {
            Log.e("EnhancedGameViewModel", "Error updating current question", e)
            _errorMessage.value = "Error loading question: ${e.message}"
        }
    }

    fun selectAnswer(option: String) {
        if (_showResult.value || option.isBlank()) return

        viewModelScope.launch {
            try {
                val currentQuestion = _currentDisplayQuestion.value ?: return@launch

                _selectedOption.value = option
                val isCorrect = option.trim().equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)
                _isAnswerCorrect.value = isCorrect

                if (isCorrect) {
                    _currentScore.value = _currentScore.value + 10
                }

                _showResult.value = true
            } catch (e: Exception) {
                Log.e("EnhancedGameViewModel", "Error selecting answer", e)
                _errorMessage.value = "Error processing answer: ${e.message}"
            }
        }
    }

    fun submitLabeling() {
        if (_showResult.value) return

        viewModelScope.launch {
            try {
                val currentQuestion = _currentDisplayQuestion.value
                val labelingData = currentQuestion?.labelingData ?: return@launch

                val currentLabels = _labeledParts.value
                val correctCount = currentLabels.count { (part, label) ->
                    part.equals(label, ignoreCase = true)
                }
                val totalParts = labelingData.imageParts.size
                _isAnswerCorrect.value = correctCount == totalParts

                val scoreIncrease = if (_isAnswerCorrect.value == true) {
                    15
                } else if (correctCount > 0) {
                    correctCount * 3
                } else {
                    0
                }

                _currentScore.value = _currentScore.value + scoreIncrease
                _showResult.value = true
            } catch (e: Exception) {
                Log.e("EnhancedGameViewModel", "Error submitting labeling", e)
                _errorMessage.value = "Error submitting labels: ${e.message}"
            }
        }
    }

    fun submitMatching() {
        if (_showResult.value) return

        viewModelScope.launch {
            try {
                val currentQuestion = _currentDisplayQuestion.value
                val matchingData = currentQuestion?.matchingData ?: return@launch

                val currentMatches = _matchedPairs.value
                val correctCount = currentMatches.count { (left, right) ->
                    matchingData.correctPairs[left] == right
                }

                val totalPairs = matchingData.correctPairs.size
                _isAnswerCorrect.value = correctCount == totalPairs

                val scoreIncrease = if (_isAnswerCorrect.value == true) {
                    20
                } else if (correctCount > 0) {
                    correctCount * 4
                } else {
                    0
                }

                _currentScore.value = _currentScore.value + scoreIncrease
                _showResult.value = true
            } catch (e: Exception) {
                Log.e("EnhancedGameViewModel", "Error submitting matching", e)
                _errorMessage.value = "Error submitting matches: ${e.message}"
            }
        }
    }

    fun onLabelSelected(label: String) {
        if (label.isBlank()) return
        _selectedLabel.value = if (_selectedLabel.value == label) null else label
    }

    fun placeLabelOnPart(partName: String) {
        val selectedLabel = _selectedLabel.value
        if (selectedLabel.isNullOrBlank() || partName.isBlank()) return

        val newLabels = _labeledParts.value.toMutableMap()
        newLabels.entries.removeAll { it.value == selectedLabel }
        newLabels[partName] = selectedLabel
        _labeledParts.value = newLabels
        _selectedLabel.value = null
    }

    fun onMatchingItemClicked(item: String, isLeftItem: Boolean) {
        if (_showResult.value || item.isBlank()) return

        if (isLeftItem) {
            _selectedLeftItem.value = if (_selectedLeftItem.value == item) null else item
        } else {
            val selectedLeft = _selectedLeftItem.value
            if (!selectedLeft.isNullOrBlank()) {
                val newMatches = _matchedPairs.value.toMutableMap()
                newMatches.entries.removeAll { it.value == item }
                newMatches[selectedLeft] = item
                _matchedPairs.value = newMatches
                _selectedLeftItem.value = null
            }
        }
    }

    fun prepareForNextInteraction() {
        viewModelScope.launch {
            try {
                val currentIndex = _currentQuestionIndex.value
                val totalQuestions = _questions.value.size

                if (currentIndex < totalQuestions - 1) {
                    _currentQuestionIndex.value = currentIndex + 1
                    resetQuestionState()
                    updateCurrentDisplayQuestion()
                } else {
                    _gameFinishedEvent.emit(Unit)
                }
            } catch (e: Exception) {
                Log.e("EnhancedGameViewModel", "Error preparing for next interaction", e)
                _errorMessage.value = "Error moving to next question: ${e.message}"
            }
        }
    }

    private fun resetQuestionState() {
        _selectedOption.value = null
        _showResult.value = false
        _isAnswerCorrect.value = null
        _labeledParts.value = emptyMap()
        _selectedLabel.value = null
        _matchedPairs.value = emptyMap()
        _selectedLeftItem.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

// App Theme and Navigation
@Composable
fun STEMLearnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2E7D32),
            secondary = Color(0xFF4CAF50),
            tertiary = Color(0xFF81C784),
            background = Color(0xFFF1F8E9),
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF1B5E20),
            onSurface = Color(0xFF1B5E20)
        ),
        content = content
    )
}

@Composable
fun StemLearningApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            AnimatedHomeScreen(navController)
        }
        composable("plant_module") {
            SubjectModuleScreen(navController, "Plant Kingdom", Subject.PLANT)
        }
        composable("animal_module") {
            SubjectModuleScreen(navController, "Animal Kingdom", Subject.ANIMAL)
        }
        composable("physics_module") {
            SubjectModuleScreen(navController, "Physics Fun", Subject.PHYSICS)
        }

        // Study routes
        composable("{subject}_study/{grade}") { backStackEntry ->
            val grade = remember(backStackEntry) {
                backStackEntry.arguments?.getString("grade")?.toIntOrNull() ?: 6
            }
            val subjectEnum = remember(backStackEntry) {
                val subjectArg = backStackEntry.arguments?.getString("subject") ?: Subject.UNKNOWN.stringValue
                Subject.fromString(subjectArg)
            }

            if (subjectEnum != Subject.UNKNOWN) {
                val viewModel: StudyViewModel = viewModel(factory = StudyViewModelFactory(grade, subjectEnum))
                StudyScreen(navController, grade, subjectEnum, viewModel)
            }
        }

        // Game routes
        composable("{subject}_game/{grade}") { backStackEntry ->
            val grade = remember(backStackEntry) {
                backStackEntry.arguments?.getString("grade")?.toIntOrNull() ?: 6
            }
            val subjectEnum = remember(backStackEntry) {
                val subjectArg = backStackEntry.arguments?.getString("subject") ?: Subject.UNKNOWN.stringValue
                Subject.fromString(subjectArg)
            }

            if (subjectEnum != Subject.UNKNOWN) {
                val viewModel: EnhancedGameViewModel = viewModel(factory = GameViewModelFactory(grade, subjectEnum))
                EnhancedGameScreen(navController, grade, subjectEnum, viewModel)
            } else {
                ErrorScreen(
                    message = "Invalid game parameters",
                    onRetry = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
        composable("profile") {
            AnimatedProfileScreen(navController)
        }
    }
}

// Error Display Components
@Composable
fun ErrorScreen(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            onRetry?.let { retryAction ->
                Button(onClick = retryAction) {
                    Text("Go Back")
                }
            }
        }
    }
}

@Composable
fun ErrorDialog(
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Oops! Something went wrong") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text("Loading content...")
        }
    }
}

// --- Screens ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedHomeScreen(navController: NavController) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EduGames", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { navController.navigate("profile") }) {
                        Icon(Icons.Default.Person, "Profile", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnimatedVisibility(isVisible, enter = slideInVertically { -it } + fadeIn()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondary),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedStatItem(Icons.Default.Star, "1250", "Points", Color(0xFFFFD700), 100)
                            AnimatedStatItem(Icons.Default.LocalFireDepartment, "7", "Day Streak", Color(0xFFFF6B35), 200)
                            AnimatedStatItem(Icons.Default.EmojiEvents, "12", "Badges", Color(0xFF9C27B0), 300)
                        }
                    }
                }
            }
            item {
                AnimatedVisibility(isVisible, enter = slideInHorizontally { it } + fadeIn()) {
                    Text(
                        "Learning Modules",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            item {
                AnimatedModuleCard(
                    title = "Plant Kingdom",
                    description = "🌱 Interactive plant labeling • Mind maps • Match games",
                    icon = Icons.Default.Park,
                    progress = 0.65f,
                    onClick = { navController.navigate("plant_module") },
                    delay = 400
                )
            }
            item {
                AnimatedModuleCard(
                    title = "Animal Kingdom",
                    description = "🦁 Animal behavior games • Habitat matching • Life cycles",
                    icon = Icons.Default.Pets,
                    progress = 0.45f,
                    onClick = { navController.navigate("animal_module") },
                    delay = 500
                )
            }
            item {
                AnimatedModuleCard(
                    title = "Physics Fun",
                    description = "⚡ Force experiments • Energy games • Wave animations",
                    icon = Icons.Default.Science,
                    progress = 0.30f,
                    onClick = { navController.navigate("physics_module") },
                    delay = 600
                )
            }
        }
    }
}

@Composable
fun AnimatedStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    delay: Long
) {
    var isVisible by remember { mutableStateOf(false) }
    val animatedValue by animateIntAsState(
        targetValue = if (isVisible) value.toIntOrNull() ?: 0 else 0,
        animationSpec = tween(1000, delay.toInt()),
        label = "StatValue"
    )

    LaunchedEffect(Unit) {
        delay(delay)
        isVisible = true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = if (value.toIntOrNull() != null) animatedValue.toString() else value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSecondary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondary.copy(0.7f)
        )
    }
}

@Composable
fun AnimatedModuleCard(
    title: String,
    description: String,
    icon: ImageVector,
    progress: Float,
    onClick: () -> Unit,
    delay: Long
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally { -it } + fadeIn()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(0.7f)
                        )
                    }
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedProgressBar(progress)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectModuleScreen(navController: NavController, title: String, subject: Subject) {
    val safePopBackStack = remember {
        {
            if (navController.currentBackStackEntry != null) {
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = safePopBackStack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Choose Your Grade",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items((6..10).toList()) { grade ->
                EnhancedGradeCard(
                    grade = grade,
                    subject = subject,
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun EnhancedGradeCard(grade: Int, subject: Subject, navController: NavController) {
    val (topics, gameTypes) = remember(subject, grade) {
        when(subject) {
            Subject.PLANT -> when(grade) {
                6 -> Pair(listOf("Tree Parts","Basic Plant Structure"), listOf("🏷️ Labeling","🧠 Mind Maps","🎯 Matching"))
                7 -> Pair(listOf("Photosynthesis","Xylem & Phloem"), listOf("🔄 Process Maps","🎪 Quiz","🏷️ Label Systems"))
                8 -> Pair(listOf("Types of Leaves","Root Systems"), listOf("🎭 Match Types","🧩 Classification Game"))
                9 -> Pair(listOf("Plant Reproduction","Flower Structure"), listOf("🌸 Flower Labeling","🎯 Match Process"))
                10 -> Pair(listOf("Plant Hormones","Advanced Botany"), listOf("⚗️ Hormone Effects","🧬 Genetics Match"))
                else -> Pair(listOf("General Topics"), listOf("Basic Games"))
            }
            Subject.ANIMAL -> Pair(listOf("Animal Topics"), listOf("Animal Games"))
            Subject.PHYSICS -> Pair(listOf("Physics Topics"), listOf("Physics Games"))
            Subject.UNKNOWN -> Pair(listOf("General Topics"), listOf("Basic Games"))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(60.dp).background(
                        Brush.radialGradient(listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )),
                        CircleShape
                    ),
                    Alignment.Center
                ) {
                    Text(
                        grade.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Class $grade", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        topics.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        gameTypes.joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { navController.navigate("${subject.stringValue}_study/$grade") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.secondary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, "Study", modifier = Modifier.size(18.dp))
                        Text("Study")
                    }
                }

                Button(
                    onClick = { navController.navigate("${subject.stringValue}_game/$grade") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, "Play", modifier = Modifier.size(18.dp))
                        Text("Play")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedGameScreen(
    navController: NavController,
    grade: Int,
    subject: Subject,
    viewModel: EnhancedGameViewModel
) {
    val currentQuestion by viewModel.currentDisplayQuestion.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val currentQuestionIndex by viewModel.currentQuestionIndex.collectAsState()
    val score by viewModel.currentScore.collectAsState()
    val showResult by viewModel.showResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val safePopBackStack = remember {
        {
            if (navController.currentBackStackEntry != null) {
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.gameFinishedEvent.collect {
            safePopBackStack()
        }
    }

    ErrorDialog(
        errorMessage = errorMessage,
        onDismiss = { viewModel.clearError() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Class $grade - ${subject.stringValue.replaceFirstChar { it.uppercase() }}") },
                navigationIcon = {
                    IconButton(onClick = safePopBackStack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate("${subject.stringValue}_study/$grade")
                    }) {
                        Icon(Icons.Default.MenuBook, "Study Mode")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                LoadingScreen()
            }
            questions.isEmpty() -> {
                ErrorScreen(
                    message = "No questions available for this grade and subject",
                    onRetry = safePopBackStack
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    AnimatedScoreCard(score, currentQuestionIndex, questions.size)
                    Spacer(Modifier.height(24.dp))

                    AnimatedContent(
                        targetState = currentQuestion,
                        transitionSpec = {
                            (fadeIn(tween(300, 150)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300, 150)))
                                .togetherWith(fadeOut(tween(150)))
                        },
                        label = "GameContent"
                    ) { question ->
                        if (question != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                when (question.type) {
                                    GameType.MCQ -> MCQGameCard(question, viewModel)
                                    GameType.LABELING -> LabelingGameCard(question, viewModel)
                                    GameType.MATCHING -> MatchingGameCard(question, viewModel)
                                    GameType.MINDMAP -> MindMapCard(question)
                                }
                                if (showResult) {
                                    Spacer(Modifier.height(16.dp))
                                    AnimatedButton(
                                        text = if (currentQuestionIndex < questions.size - 1) "Next Challenge" else "Finish Adventure",
                                        onClick = { viewModel.prepareForNextInteraction() }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No question available",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Shared Components ---

@Composable
fun AnimatedProgressBar(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val currentProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = tween(1000),
        label = "ProgressBar"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { currentProgress },
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.secondary.copy(0.3f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${(currentProgress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(0.7f)
        )
    }
}

@Composable
fun AnimatedScoreCard(score: Int, currentIndex: Int, totalQuestions: Int) {
    val safeScore = score.coerceAtLeast(0)
    val safeCurrentIndex = currentIndex.coerceAtLeast(0)
    val safeTotalQuestions = totalQuestions.coerceAtLeast(1)

    val animatedScore by animateIntAsState(
        targetValue = safeScore,
        animationSpec = tween(500),
        label = "ScoreValue"
    )
    val progress by animateFloatAsState(
        targetValue = (safeCurrentIndex + 1f) / safeTotalQuestions,
        animationSpec = tween(800),
        label = "ScoreProgress"
    )

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondary),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, "Score", tint = Color(0xFFFFD700))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Score: $animatedScore",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
                Text(
                    "Question ${safeCurrentIndex + 1}/$safeTotalQuestions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(0.3f)
            )
        }
    }
}

@Composable
fun AnimatedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
        elevation = ButtonDefaults.buttonElevation(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.NavigateNext, contentDescription = null)
            Text(text)
        }
    }
}

@Composable
fun ResultText(isCorrect: Boolean?) {
    val (text, color) = when (isCorrect) {
        true -> "🎉 Excellent! You got it right!" to Color(0xFF2E7D32)
        false -> "💪 Good try! Keep learning!" to Color(0xFFC62828)
        else -> "" to Color.Unspecified
    }
    AnimatedVisibility(
        visible = text.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Game Components

@Composable
fun MCQGameCard(question: GameQuestion, viewModel: EnhancedGameViewModel) {
    val selectedOption by viewModel.selectedOption.collectAsState()
    val showResult by viewModel.showResult.collectAsState()
    val isCorrect by viewModel.isAnswerCorrect.collectAsState()

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                question.question,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            if (question.options.isNotEmpty()) {
                question.options.forEach { option ->
                    val answerState = when {
                        !showResult -> null
                        option == question.correctAnswer -> true
                        option == selectedOption -> false
                        else -> null
                    }
                    AnimatedAnswerButton(
                        text = option,
                        isSelected = option == selectedOption,
                        isCorrect = answerState,
                        enabled = !showResult,
                        onClick = { viewModel.selectAnswer(option) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                Text(
                    "No options available for this question",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            if (showResult) ResultText(isCorrect)
        }
    }
}

@Composable
fun AnimatedAnswerButton(
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "AnswerButtonScale"
    )
    val color by animateColorAsState(
        label = "AnswerButtonColor",
        targetValue = when (isCorrect) {
            true -> Color(0xFF4CAF50)
            false -> Color(0xFFF44336)
            null -> if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        }
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (isSelected || isCorrect != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContainerColor = color.copy(0.7f),
            disabledContentColor = (if (isSelected || isCorrect != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant).copy(0.7f)
        ),
        elevation = ButtonDefaults.buttonElevation(if (isSelected) 6.dp else 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                Modifier.weight(1f).padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
            AnimatedVisibility(isCorrect != null) {
                Icon(
                    if (isCorrect == true) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LabelingGameCard(question: GameQuestion, viewModel: EnhancedGameViewModel) {
    val showResult by viewModel.showResult.collectAsState()
    val isCorrect by viewModel.isAnswerCorrect.collectAsState()

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            question.labelingData?.let { data ->
                if (data.imageParts.isNotEmpty() && data.labels.isNotEmpty()) {
                    PlantDiagram(
                        parts = data.imageParts,
                        labels = data.labels,
                        labeledParts = viewModel.labeledParts.collectAsState().value,
                        selectedLabel = viewModel.selectedLabel.collectAsState().value,
                        onLabelSelect = viewModel::onLabelSelected,
                        onPartTap = viewModel::placeLabelOnPart,
                        showResult = showResult
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (!showResult) {
                        AnimatedButton(
                            text = "Submit Labels",
                            onClick = { viewModel.submitLabeling() }
                        )
                    } else {
                        ResultText(isCorrect)
                    }
                } else {
                    Text(
                        "Labeling data is incomplete",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            } ?: Text(
                "No labeling data available",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PlantDiagram(
    parts: List<PlantPart>,
    labels: List<String>,
    labeledParts: Map<String, String>,
    selectedLabel: String?,
    onLabelSelect: (String) -> Unit,
    onPartTap: (String) -> Unit,
    showResult: Boolean
) {
    val diagramHeight = 300.dp
    Row(modifier = Modifier.fillMaxWidth().height(diagramHeight)) {
        LazyColumn(
            Modifier.weight(0.4f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(labels) { label ->
                if (!labeledParts.containsValue(label)) {
                    SelectableLabel(
                        label = label,
                        isSelected = label == selectedLabel,
                        onClick = { onLabelSelect(label) }
                    )
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        BoxWithConstraints(
            modifier = Modifier.weight(0.6f).fillMaxHeight()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFF98FB98))),
                    RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) {
            DrawPlant()
            parts.forEach { part ->
                val safeX = part.x.coerceIn(0f, 0.9f)
                val safeY = part.y.coerceIn(0f, 0.9f)
                DropZone(
                    part = part,
                    labeledParts = labeledParts,
                    onPartTap = onPartTap,
                    showResult = showResult,
                    modifier = Modifier.offset(
                        x = maxWidth * safeX,
                        y = maxHeight * safeY
                    )
                )
            }
        }
    }
}

@Composable
fun DrawPlant() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stemWidth = 8.dp.toPx()
        drawRect(
            Color(0xFF8FBC8F),
            Offset(center.x - stemWidth / 2, size.height * 0.3f),
            androidx.compose.ui.geometry.Size(stemWidth, size.height * 0.5f)
        )
        drawCircle(Color(0xFF228B22), 20.dp.toPx(), Offset(center.x - 20.dp.toPx(), size.height * 0.4f))
        drawCircle(Color(0xFF228B22), 20.dp.toPx(), Offset(center.x + 20.dp.toPx(), size.height * 0.5f))

        val rootPath = Path().apply {
            moveTo(center.x, size.height * 0.8f)
            lineTo(center.x - 20.dp.toPx(), size.height * 0.9f)
            moveTo(center.x, size.height * 0.8f)
            lineTo(center.x + 15.dp.toPx(), size.height * 0.92f)
            moveTo(center.x, size.height * 0.8f)
            lineTo(center.x, size.height)
        }
        drawPath(rootPath, Color(0xFFA0522D), style = Stroke(2.dp.toPx()))
    }
}

@Composable
fun DropZone(
    part: PlantPart,
    labeledParts: Map<String, String>,
    onPartTap: (String) -> Unit,
    showResult: Boolean,
    modifier: Modifier = Modifier
) {
    val currentLabel = labeledParts[part.name]
    val isCorrect = currentLabel?.equals(part.name, ignoreCase = true) == true
    val color by animateColorAsState(
        label = "DropZoneColor",
        targetValue = when {
            showResult && isCorrect -> Color(0xFF4CAF50)
            showResult && currentLabel != null -> Color(0xFFF44336)
            currentLabel != null -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        }
    )

    Box(
        modifier.size(50.dp)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .clickable { onPartTap(part.name) },
        Alignment.Center
    ) {
        if (currentLabel != null) {
            Text(
                currentLabel.take(3).uppercase(),
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                "?",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SelectableLabel(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val elevation by animateDpAsState(if (isSelected) 8.dp else 2.dp, label = "LabelElevation")
    Card(
        Modifier.width(100.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(elevation)
    ) {
        Text(
            label,
            Modifier.padding(12.dp).fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MatchingGameCard(question: GameQuestion, viewModel: EnhancedGameViewModel) {
    val showResult by viewModel.showResult.collectAsState()
    val isCorrect by viewModel.isAnswerCorrect.collectAsState()

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                question.question,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            question.matchingData?.let { data ->
                if (data.leftItems.isNotEmpty() && data.rightItems.isNotEmpty()) {
                    val matchedPairs by viewModel.matchedPairs.collectAsState()
                    val selectedLeftItem by viewModel.selectedLeftItem.collectAsState()
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MatchingColumn(
                            modifier = Modifier.weight(1f),
                            items = data.leftItems,
                            isLeft = true,
                            matchedPairs = matchedPairs,
                            selectedItem = selectedLeftItem,
                            showResult = showResult,
                            correctPairs = data.correctPairs,
                            onClick = viewModel::onMatchingItemClicked
                        )
                        MatchingColumn(
                            modifier = Modifier.weight(1f),
                            items = data.rightItems,
                            isLeft = false,
                            matchedPairs = matchedPairs,
                            selectedItem = selectedLeftItem,
                            showResult = showResult,
                            correctPairs = data.correctPairs,
                            onClick = viewModel::onMatchingItemClicked
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!showResult) {
                        AnimatedButton(
                            text = "Submit Matches",
                            onClick = { viewModel.submitMatching() }
                        )
                    } else {
                        ResultText(isCorrect)
                    }
                } else {
                    Text(
                        "Matching data is incomplete",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            } ?: Text(
                "No matching data available",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MatchingColumn(
    modifier: Modifier,
    items: List<String>,
    isLeft: Boolean,
    matchedPairs: Map<String, String>,
    selectedItem: String?,
    showResult: Boolean,
    correctPairs: Map<String, String>,
    onClick: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(items) { item ->
            val isMatched = if (isLeft) matchedPairs.containsKey(item) else matchedPairs.containsValue(item)
            val isCorrect = when {
                !showResult -> false
                isLeft -> correctPairs[item] == matchedPairs[item]
                else -> matchedPairs.any { (k, v) -> v == item && correctPairs[k] == v }
            }
            if (isMatched && !showResult) {
                Spacer(Modifier.height(48.dp))
            } else {
                MatchingItem(
                    text = item,
                    isSelected = item == selectedItem,
                    isMatched = isMatched,
                    isCorrect = isCorrect,
                    showResult = showResult,
                    onClick = { onClick(item, isLeft) }
                )
            }
        }
    }
}

@Composable
fun MatchingItem(
    text: String,
    isSelected: Boolean,
    isMatched: Boolean,
    isCorrect: Boolean,
    showResult: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        label = "MatchingItemColor",
        targetValue = when {
            showResult && isCorrect -> Color(0xFF4CAF50)
            showResult && !isCorrect && isMatched -> Color(0xFFF44336)
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    )

    Card(
        Modifier.fillMaxWidth().height(48.dp).clickable(enabled = !showResult, onClick = onClick),
        colors = CardDefaults.cardColors(color),
        elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text,
                textAlign = TextAlign.Center,
                color = if (isSelected || (showResult && isMatched)) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun MindMapCard(question: GameQuestion) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                question.question,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            question.mindMapData?.let { data ->
                if (data.branches.isNotEmpty()) {
                    MindMapView(data)
                } else {
                    Text(
                        "Mind map data is incomplete",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            } ?: Text(
                "No mind map data available",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MindMapView(data: MindMapData) {
    Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Text(
                data.centralTopic,
                Modifier.padding(16.dp),
                MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        data.branches.forEachIndexed { index, branch ->
            val angle = (2 * Math.PI * index / data.branches.size).toFloat()
            val radius = 120.dp
            Card(
                Modifier.offset(radius * cos(angle), radius * sin(angle)).widthIn(max = 100.dp),
                colors = CardDefaults.cardColors(branch.color),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        branch.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    branch.items.forEach {
                        Text("• $it", fontSize = 10.sp, color = Color.White.copy(0.9f))
                    }
                }
            }
        }
    }
}

// Study Screen Components
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    navController: NavController,
    grade: Int,
    subject: Subject,
    viewModel: StudyViewModel
) {
    val studyTopics by viewModel.studyTopics.collectAsState()
    val currentTopicIndex by viewModel.currentTopicIndex.collectAsState()
    val currentConceptIndex by viewModel.currentConceptIndex.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val safePopBackStack = remember {
        {
            if (navController.currentBackStackEntry != null) {
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study: Class $grade - ${subject.stringValue.replaceFirstChar { it.uppercase() }}") },
                navigationIcon = {
                    IconButton(onClick = safePopBackStack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate("${subject.stringValue}_game/$grade")
                    }) {
                        Icon(Icons.Default.PlayArrow, "Play Games")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            LoadingScreen()
        } else if (studyTopics.isEmpty()) {
            ErrorScreen("No study content available")
        } else {
            val currentTopic = studyTopics.getOrNull(currentTopicIndex)
            val currentConcept = currentTopic?.concepts?.getOrNull(currentConceptIndex)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                StudyProgressCard(currentTopicIndex, currentConceptIndex, studyTopics)

                Spacer(modifier = Modifier.height(16.dp))

                if (currentTopic != null && currentConcept != null) {
                    StudyConceptCard(
                        topic = currentTopic,
                        concept = currentConcept,
                        onNext = { viewModel.nextConcept() },
                        onPrevious = { viewModel.previousConcept() },
                        hasNext = hasNextConcept(currentTopicIndex, currentConceptIndex, studyTopics),
                        hasPrevious = hasPreviousConcept(currentTopicIndex, currentConceptIndex)
                    )
                }
            }
        }
    }
}

@Composable
fun StudyProgressCard(
    currentTopicIndex: Int,
    currentConceptIndex: Int,
    studyTopics: List<StudyTopic>
) {
    val totalConcepts = studyTopics.sumOf { it.concepts.size }
    val completedConcepts = studyTopics.take(currentTopicIndex).sumOf { it.concepts.size } + currentConceptIndex
    val progress = if (totalConcepts > 0) completedConcepts.toFloat() / totalConcepts else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondary),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, "Study Progress", tint = Color(0xFFFFD700))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Study Progress",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
                Text(
                    "${completedConcepts + 1}/$totalConcepts",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(0.3f)
            )
        }
    }
}

@Composable
fun StudyConceptCard(
    topic: StudyTopic,
    concept: StudyConcept,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    hasNext: Boolean,
    hasPrevious: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        topic.icon,
                        fontSize = 32.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            topic.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            concept.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            item {
                ConceptSection(
                    title = "📖 What is it?",
                    content = concept.definition
                )
            }

            item {
                ExpandableSection(
                    title = "💡 Key Points",
                    items = concept.keyPoints,
                    icon = "•"
                )
            }

            if (concept.examples.isNotEmpty()) {
                item {
                    ExpandableSection(
                        title = "🔍 Examples",
                        items = concept.examples,
                        icon = "→"
                    )
                }
            }

            concept.interactiveElement?.let { interactive ->
                item {
                    InteractiveElementCard(interactive)
                }
            }

            if (concept.funFacts.isNotEmpty()) {
                item {
                    FunFactsCard(concept.funFacts)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (hasPrevious) {
                        AnimatedStudyButton(
                            text = "Previous",
                            icon = Icons.Default.ArrowBack,
                            onClick = onPrevious
                        )
                    } else {
                        Spacer(modifier = Modifier.width(100.dp))
                    }

                    if (hasNext) {
                        AnimatedStudyButton(
                            text = "Next",
                            icon = Icons.Default.ArrowForward,
                            onClick = onNext
                        )
                    } else {
                        AnimatedStudyButton(
                            text = "Complete!",
                            icon = Icons.Default.CheckCircle,
                            onClick = { /* Handle completion */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConceptSection(title: String, content: String) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun ExpandableSection(title: String, items: List<String>, icon: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                icon,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                item,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveElementCard(interactive: InteractiveElement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🎮 Interactive Learning",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when (interactive) {
                is InteractiveElement.ImageClick -> ImageClickWidget(interactive.parts)
                is InteractiveElement.DragDrop -> DragDropWidget(interactive.items, interactive.categories)
                is InteractiveElement.Timeline -> TimelineWidget(interactive.events)
                is InteractiveElement.Comparison -> ComparisonWidget(interactive.items)
            }
        }
    }
}

@Composable
fun ImageClickWidget(parts: List<ClickablePart>) {
    var selectedPart by remember { mutableStateOf<ClickablePart?>(null) }

    Column {
        Text("Tap on different parts to learn more!",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFF98FB98))),
                    RoundedCornerShape(8.dp)
                )
        ) {
            parts.forEach { part ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (300 * part.x).dp,
                            y = (150 * part.y).dp
                        )
                        .size(40.dp)
                        .background(
                            if (selectedPart == part) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary,
                            CircleShape
                        )
                        .clickable { selectedPart = part },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        part.name.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        selectedPart?.let { part ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        part.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        part.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineWidget(events: List<TimelineEvent>) {
    var selectedEvent by remember { mutableStateOf<TimelineEvent?>(null) }

    Column {
        Text("Follow the process step by step:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp))

        events.forEachIndexed { index, event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { selectedEvent = if (selectedEvent == event) null else event }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (selectedEvent == event) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (index + 1).toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        event.step,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(0.7f)
                    )
                }
            }

            if (selectedEvent == event) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, top = 4.dp, bottom = 8.dp),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        event.detail,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DragDropWidget(items: List<String>, categories: List<String>) {
    Text("Match items with categories (tap to see answers):",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 8.dp))

    var showAnswers by remember { mutableStateOf(false) }

    Button(
        onClick = { showAnswers = !showAnswers },
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Text(if (showAnswers) "Hide Answers" else "Show Answers")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        categories.forEach { category ->
            Card(
                modifier = Modifier.weight(1f).padding(4.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        category,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showAnswers) {
                        items.take(2).forEach { item ->
                            Text(
                                "• $item",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonWidget(items: Map<String, List<String>>) {
    Text("Compare different aspects:",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { (title, features) ->
            Card(
                modifier = Modifier.weight(1f).padding(4.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    features.forEach { feature ->
                        Text(
                            "• $feature",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FunFactsCard(facts: List<String>) {
    var currentFactIndex by remember { mutableStateOf(0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🌟 Did You Know?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            AnimatedContent(
                targetState = facts[currentFactIndex],
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "FunFact"
            ) { fact ->
                Text(
                    fact,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        currentFactIndex = if (currentFactIndex > 0) currentFactIndex - 1 else facts.size - 1
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, "Previous fact")
                }

                Text(
                    "${currentFactIndex + 1} / ${facts.size}",
                    style = MaterialTheme.typography.bodySmall
                )

                IconButton(
                    onClick = {
                        currentFactIndex = if (currentFactIndex < facts.size - 1) currentFactIndex + 1 else 0
                    }
                ) {
                    Icon(Icons.Default.ArrowForward, "Next fact")
                }
            }
        }
    }
}

@Composable
fun AnimatedStudyButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
        elevation = ButtonDefaults.buttonElevation(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Text(text)
        }
    }
}

// Helper functions
fun hasNextConcept(topicIndex: Int, conceptIndex: Int, topics: List<StudyTopic>): Boolean {
    if (topicIndex >= topics.size) return false
    val topic = topics[topicIndex]
    return conceptIndex < topic.concepts.size - 1 || topicIndex < topics.size - 1
}

fun hasPreviousConcept(topicIndex: Int, conceptIndex: Int): Boolean {
    return topicIndex > 0 || conceptIndex > 0
}

// Profile Screen Components

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedProfileScreen(navController: NavController) {
    val safePopBackStack = remember {
        {
            if (navController.currentBackStackEntry != null) {
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = safePopBackStack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ProfileSection("User Profile") { ProfileHeaderCard() } }
            item { ProfileSection("🏆 Achievements") { AchievementGrid() } }
            item { ProfileSection("📊 Statistics") { EnhancedStatsCard() } }
        }
    }
}

@Composable
fun ProfileSection(title: String, content: @Composable () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    AnimatedVisibility(isVisible, enter = fadeIn(tween(300, 100)) + slideInVertically { it / 2 }) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun ProfileHeaderCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondary),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(100.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    ))),
                Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    "Profile Icon",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(50.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "🎓 Rural STEM Explorer",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondary
            )
            Text(
                "Grade 8 • 🏫 Village Science Academy",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondary.copy(0.9f)
            )
        }
    }
}

@Composable
fun AchievementGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AnimatedAchievementBadge("🌱", "Plant Master", "Completed plant module", 200)
            AnimatedAchievementBadge("🦁", "Animal Expert", "Studied animal kingdom", 400)
            AnimatedAchievementBadge("⚡", "Physics Pro", "Understood physics", 600)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AnimatedAchievementBadge("🔥", "Streak Legend", "15 day streak", 800)
            AnimatedAchievementBadge("⭐", "Quiz Champion", "95% average score", 1000)
            AnimatedAchievementBadge("🏆", "STEM Hero", "Mastered all modules", 1200)
        }
    }
}

@Composable
fun AnimatedAchievementBadge(emoji: String, title: String, description: String, delay: Long) {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "BadgeScale"
    )
    LaunchedEffect(Unit) {
        delay(delay)
        isVisible = true
    }

    Card(
        Modifier.size(width = 110.dp, height = 140.dp).scale(scale),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 28.sp)
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = LocalContentColor.current.copy(0.7f),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun EnhancedStatsCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            val stats = listOf(
                "🎯 Total Points" to "3,250",
                "📚 Lessons Completed" to "42",
                "🎪 Interactive Games" to "28",
                "📈 Average Score" to "94%",
                "⏱️ Time Spent Learning" to "24h 15m",
                "🔥 Current Streak" to "15 days",
                "🎮 Favorite Game Type" to "Labeling",
                "🏅 Global Rank" to "#247"
            )
            stats.forEachIndexed { index, (label, value) ->
                AnimatedStatRow(label, value, index * 100L)
            }
        }
    }
}

@Composable
fun AnimatedStatRow(label: String, value: String, delay: Long) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delay)
        isVisible = true
    }
    AnimatedVisibility(
        isVisible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current.copy(0.8f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// --- Data Functions ---

fun getEnhancedPlantQuestions(grade: Int): List<GameQuestion> {
    return when(grade) {
        6 -> listOf(
            GameQuestion(
                "Which part of the plant absorbs water and nutrients?",
                GameType.MCQ,
                listOf("Leaves", "Roots", "Stem", "Flowers"),
                "Roots"
            ),
            GameQuestion(
                "🏷️ Label the parts of this plant correctly!",
                GameType.LABELING,
                labelingData = LabelingGameData(
                    listOf(
                        PlantPart("Leaves", 0.3f, 0.3f),
                        PlantPart("Stem", 0.45f, 0.55f),
                        PlantPart("Roots", 0.45f, 0.85f)
                    ),
                    listOf("Leaves", "Stem", "Roots")
                )
            ),
            GameQuestion(
                "🎯 Match each plant part with its function!",
                GameType.MATCHING,
                matchingData = MatchingGameData(
                    listOf("Roots", "Leaves", "Stem"),
                    listOf("Absorb water", "Make food", "Support plant"),
                    mapOf(
                        "Roots" to "Absorb water",
                        "Leaves" to "Make food",
                        "Stem" to "Support plant"
                    )
                )
            ),
            GameQuestion(
                "🧠 Explore the Plant Parts Mind Map",
                GameType.MINDMAP,
                mindMapData = MindMapData(
                    "PLANT PARTS",
                    listOf(
                        MindMapBranch("Roots", listOf("Underground", "Absorb water", "Anchor plant"), Color(0xFF8D6E63)),
                        MindMapBranch("Stem", listOf("Support", "Transport", "Green/Brown"), Color(0xFF4CAF50)),
                        MindMapBranch("Leaves", listOf("Photosynthesis", "Green color", "Different shapes"), Color(0xFF2E7D32))
                    )
                )
            )
        )
        7 -> listOf(
            GameQuestion(
                "What do plants produce during photosynthesis?",
                GameType.MCQ,
                listOf("Carbon dioxide", "Water", "Oxygen", "Nitrogen"),
                "Oxygen"
            ),
            GameQuestion(
                "🎯 Match the transport system with what it carries!",
                GameType.MATCHING,
                matchingData = MatchingGameData(
                    listOf("Xylem", "Phloem"),
                    listOf("Water & minerals", "Food & sugar"),
                    mapOf(
                        "Xylem" to "Water & minerals",
                        "Phloem" to "Food & sugar"
                    )
                )
            ),
            GameQuestion(
                "🧠 Understanding Photosynthesis Process",
                GameType.MINDMAP,
                mindMapData = MindMapData(
                    "PHOTOSYNTHESIS",
                    listOf(
                        MindMapBranch("Inputs", listOf("Sunlight", "Water", "CO2"), Color(0xFFFF9800)),
                        MindMapBranch("Process", listOf("Chlorophyll", "Reaction", "In leaves"), Color(0xFF4CAF50)),
                        MindMapBranch("Outputs", listOf("Glucose", "Oxygen", "Energy"), Color(0xFF2196F3))
                    )
                )
            )
        )
        8 -> listOf(
            GameQuestion(
                "Which type of root system has one main root?",
                GameType.MCQ,
                listOf("Fibrous", "Tap root", "Adventitious", "Prop root"),
                "Tap root"
            ),
            GameQuestion(
                "🎯 Match leaf types with their characteristics!",
                GameType.MATCHING,
                matchingData = MatchingGameData(
                    listOf("Simple leaf", "Compound leaf", "Needle leaf"),
                    listOf("Single blade", "Multiple leaflets", "Narrow & pointed"),
                    mapOf(
                        "Simple leaf" to "Single blade",
                        "Compound leaf" to "Multiple leaflets",
                        "Needle leaf" to "Narrow & pointed"
                    )
                )
            )
        )
        9 -> listOf(
            GameQuestion(
                "What is the male reproductive part of a flower?",
                GameType.MCQ,
                listOf("Pistil", "Stamen", "Sepal", "Petal"),
                "Stamen"
            ),
            GameQuestion(
                "🏷️ Label the parts of a flower!",
                GameType.LABELING,
                labelingData = LabelingGameData(
                    listOf(
                        PlantPart("Petal", 0.3f, 0.3f),
                        PlantPart("Stamen", 0.45f, 0.4f),
                        PlantPart("Pistil", 0.45f, 0.55f)
                    ),
                    listOf("Petal", "Stamen", "Pistil")
                )
            )
        )
        10 -> listOf(
            GameQuestion(
                "Which hormone promotes cell elongation in plants?",
                GameType.MCQ,
                listOf("Gibberellin", "Cytokinin", "Auxin", "Ethylene"),
                "Auxin"
            ),
            GameQuestion(
                "🧠 Plant Hormones and Their Effects",
                GameType.MINDMAP,
                mindMapData = MindMapData(
                    "PLANT HORMONES",
                    listOf(
                        MindMapBranch("Auxin", listOf("Growth", "Bending", "Elongation"), Color(0xFF9C27B0)),
                        MindMapBranch("Gibberellin", listOf("Stem growth", "Flowering", "Germination"), Color(0xFFFF5722)),
                        MindMapBranch("Cytokinin", listOf("Cell division", "Leaf growth", "Delay aging"), Color(0xFF00BCD4))
                    )
                )
            )
        )
        else -> emptyList()
    }
}

fun getEnhancedAnimalQuestions(grade: Int): List<GameQuestion> {
    return when(grade) {
        6 -> listOf(
            GameQuestion(
                "Which animals are warm-blooded?",
                GameType.MCQ,
                listOf("Fish", "Reptiles", "Birds", "Insects"),
                "Birds"
            ),
            GameQuestion(
                "🎯 Match animals with their food type!",
                GameType.MATCHING,
                matchingData = MatchingGameData(
                    listOf("Lion", "Cow", "Bear"),
                    listOf("Meat", "Grass", "Both"),
                    mapOf(
                        "Lion" to "Meat",
                        "Cow" to "Grass",
                        "Bear" to "Both"
                    )
                )
            )
        )
        else -> listOf(
            GameQuestion(
                "Sample animal question for grade $grade",
                GameType.MCQ,
                listOf("Option 1", "Option 2", "Option 3", "Option 4"),
                "Option 1"
            )
        )
    }
}

fun getEnhancedPhysicsQuestions(grade: Int): List<GameQuestion> {
    return when(grade) {
        6 -> listOf(
            GameQuestion(
                "Which is a simple machine?",
                GameType.MCQ,
                listOf("Computer", "Lever", "Television", "Phone"),
                "Lever"
            ),
            GameQuestion(
                "🎯 Match simple machines with their uses!",
                GameType.MATCHING,
                matchingData = MatchingGameData(
                    listOf("Lever", "Pulley", "Wheel"),
                    listOf("Lift heavy objects", "Raise loads", "Reduce friction"),
                    mapOf(
                        "Lever" to "Lift heavy objects",
                        "Pulley" to "Raise loads",
                        "Wheel" to "Reduce friction"
                    )
                )
            )
        )
        else -> listOf(
            GameQuestion(
                "Sample physics question for grade $grade",
                GameType.MCQ,
                listOf("Option 1", "Option 2", "Option 3", "Option 4"),
                "Option 1"
            )
        )
    }
}

// Study Data Functions
// Study Data Functions
fun getPlantStudyTopics(grade: Int): List<StudyTopic> {
    return when(grade) {
        6 -> listOf(
            StudyTopic(
                title = "Basic Plant Structure",
                icon = "🌱",
                concepts = listOf(
                    StudyConcept(
                        title = "What are Plants?",
                        definition = "Plants are living organisms that make their own food using sunlight, water, and air. They are essential for life on Earth!",
                        keyPoints = listOf(
                            "Plants are living things that can make their own food",
                            "They need sunlight, water, and carbon dioxide",
                            "Plants give us oxygen to breathe",
                            "They have different parts with special jobs"
                        ),
                        examples = listOf("Trees like mango and banyan", "Flowers like rose and sunflower", "Vegetables like spinach and tomato"),
                        interactiveElement = InteractiveElement.ImageClick(
                            listOf(
                                ClickablePart("Leaves", 0.3f, 0.2f, "Leaves make food for the plant using sunlight"),
                                ClickablePart("Stem", 0.5f, 0.5f, "Stem supports the plant and carries water"),
                                ClickablePart("Roots", 0.5f, 0.8f, "Roots absorb water and nutrients from soil")
                            )
                        ),
                        funFacts = listOf(
                            "The largest living plant is a fungus that covers 2,400 acres!",
                            "Some plants can live for thousands of years",
                            "Plants produce the oxygen we breathe every day"
                        )
                    ),
                    StudyConcept(
                        title = "Plant Parts and Their Jobs",
                        definition = "Every part of a plant has a special job to help the plant survive and grow healthy.",
                        keyPoints = listOf(
                            "Roots anchor the plant and absorb water",
                            "Stem supports leaves and transports materials",
                            "Leaves make food through photosynthesis",
                            "Flowers help plants reproduce"
                        ),
                        examples = listOf(
                            "Carrot roots store food",
                            "Bamboo stems grow very tall",
                            "Banana leaves are very large"
                        ),
                        interactiveElement = InteractiveElement.Comparison(
                            mapOf(
                                "Roots" to listOf("Underground", "Absorb water", "Store food", "Anchor plant"),
                                "Stem" to listOf("Above ground", "Support plant", "Transport water", "Can be thick or thin"),
                                "Leaves" to listOf("Green color", "Make food", "Different shapes", "Need sunlight")
                            )
                        )
                    )
                )
            ),
            StudyTopic(
                title = "How Plants Make Food",
                icon = "☀️",
                concepts = listOf(
                    StudyConcept(
                        title = "Photosynthesis - Nature's Kitchen",
                        definition = "Photosynthesis is the amazing process where plants make their own food using sunlight, just like a magical kitchen!",
                        keyPoints = listOf(
                            "Plants use sunlight as energy",
                            "They take in carbon dioxide from air",
                            "Roots absorb water from soil",
                            "Leaves combine everything to make food"
                        ),
                        examples = listOf(
                            "Trees making food during sunny days",
                            "Green leaves working like solar panels",
                            "Plants growing faster in sunlight"
                        ),
                        interactiveElement = InteractiveElement.Timeline(
                            listOf(
                                TimelineEvent(
                                    "Sunlight Hits Leaves",
                                    "Solar energy reaches the plant",
                                    "Chlorophyll in leaves captures the sun's energy like tiny solar panels"
                                ),
                                TimelineEvent(
                                    "Water Travels Up",
                                    "Roots send water to leaves",
                                    "Water moves through the stem like pipes carrying water to every part"
                                ),
                                TimelineEvent(
                                    "Air Goes In",
                                    "Leaves absorb carbon dioxide",
                                    "Small openings in leaves breathe in CO2 from the air around us"
                                ),
                                TimelineEvent(
                                    "Food is Made",
                                    "All ingredients combine to make sugar",
                                    "The plant's kitchen mixes everything to create sweet glucose for energy"
                                ),
                                TimelineEvent(
                                    "Oxygen Released",
                                    "Plant gives us fresh air",
                                    "As a bonus, plants release oxygen that we need to breathe!"
                                )
                            )
                        ),
                        funFacts = listOf(
                            "One large tree produces enough oxygen for two people per day!",
                            "Plants have been making oxygen for over 2 billion years",
                            "Without photosynthesis, there would be no life on Earth"
                        )
                    )
                )
            )
        )
        7 -> listOf(
            StudyTopic(
                title = "Advanced Plant Systems",
                icon = "🌿",
                concepts = listOf(
                    StudyConcept(
                        title = "Transport in Plants",
                        definition = "Plants have special pipes called xylem and phloem that transport water, nutrients, and food throughout the plant body.",
                        keyPoints = listOf(
                            "Xylem carries water and minerals from roots to leaves",
                            "Phloem carries food from leaves to all plant parts",
                            "Transport happens without any pump or heart",
                            "Transpiration helps pull water up through the plant"
                        ),
                        examples = listOf(
                            "Water moving up a tall tree trunk",
                            "Sugar made in leaves reaching the roots",
                            "Nutrients from soil reaching flower petals"
                        ),
                        interactiveElement = InteractiveElement.Comparison(
                            mapOf(
                                "Xylem" to listOf("Carries water up", "Made of dead cells", "Like plant's water pipes", "Helps plant stand tall"),
                                "Phloem" to listOf("Carries food around", "Made of living cells", "Like plant's food delivery", "Feeds all plant parts")
                            )
                        ),
                        funFacts = listOf(
                            "Water can travel over 100 meters up in tall trees!",
                            "Plants don't have hearts but still move liquids efficiently",
                            "A single leaf can transpire several cups of water per day"
                        )
                    )
                )
            )
        )
        8 -> listOf(
            StudyTopic(
                title = "Types of Plants",
                icon = "🌳",
                concepts = listOf(
                    StudyConcept(
                        title = "Root Systems",
                        definition = "Plants have different types of root systems that help them survive in various environments.",
                        keyPoints = listOf(
                            "Tap roots have one main root going deep",
                            "Fibrous roots spread out like a net",
                            "Adventitious roots grow from stems or leaves",
                            "Different roots help plants in different ways"
                        ),
                        examples = listOf(
                            "Carrot has a tap root system",
                            "Grass has fibrous root system",
                            "Banyan tree has prop roots"
                        ),
                        interactiveElement = InteractiveElement.DragDrop(
                            listOf("Carrot", "Radish", "Grass", "Rice", "Corn", "Wheat"),
                            listOf("Tap Root", "Fibrous Root")
                        )
                    )
                )
            )
        )
        else -> listOf(
            StudyTopic(
                title = "Plant Basics",
                icon = "🌱",
                concepts = listOf(
                    StudyConcept(
                        title = "Introduction to Plants",
                        definition = "Plants are amazing living things that make our world green and provide us with food and oxygen.",
                        keyPoints = listOf(
                            "Plants are everywhere around us",
                            "They come in many shapes and sizes",
                            "Plants are very important for life",
                            "We can learn a lot from studying plants"
                        ),
                        examples = listOf("Trees", "Flowers", "Grass", "Vegetables")
                    )
                )
            )
        )
    }
}

fun getAnimalStudyTopics(grade: Int): List<StudyTopic> {
    return when(grade) {
        6 -> listOf(
            StudyTopic(
                title = "Animal Classification",
                icon = "🐾",
                concepts = listOf(
                    StudyConcept(
                        title = "Types of Animals",
                        definition = "Animals can be grouped based on their characteristics like body temperature, backbone, and habitat.",
                        keyPoints = listOf(
                            "Warm-blooded animals maintain constant body temperature",
                            "Cold-blooded animals' temperature changes with environment",
                            "Vertebrates have backbones, invertebrates don't",
                            "Animals live in different habitats"
                        ),
                        examples = listOf(
                            "Birds and mammals are warm-blooded",
                            "Fish and reptiles are cold-blooded",
                            "Elephants are vertebrates, insects are invertebrates"
                        ),
                        interactiveElement = InteractiveElement.DragDrop(
                            listOf("Cat", "Fish", "Bird", "Snake", "Butterfly", "Dog"),
                            listOf("Warm-blooded", "Cold-blooded")
                        ),
                        funFacts = listOf(
                            "Hummingbirds have the fastest heartbeat of any bird!",
                            "Some fish can change their body temperature",
                            "There are more insects than all other animals combined"
                        )
                    ),
                    StudyConcept(
                        title = "Animal Habitats",
                        definition = "A habitat is a natural environment where animals live, find food, and raise their young.",
                        keyPoints = listOf(
                            "Forest animals like tigers and monkeys",
                            "Ocean animals like whales and dolphins",
                            "Desert animals like camels and lizards",
                            "Arctic animals like polar bears and penguins"
                        ),
                        examples = listOf(
                            "Fish live in water habitats",
                            "Birds build nests in trees",
                            "Rabbits live in burrows underground"
                        ),
                        interactiveElement = InteractiveElement.Comparison(
                            mapOf(
                                "Forest" to listOf("Tigers", "Monkeys", "Trees everywhere", "Lots of rain"),
                                "Ocean" to listOf("Whales", "Fish", "Salty water", "Very deep"),
                                "Desert" to listOf("Camels", "Snakes", "Very hot", "Little water")
                            )
                        )
                    )
                )
            )
        )
        7 -> listOf(
            StudyTopic(
                title = "Animal Behavior",
                icon = "🦁",
                concepts = listOf(
                    StudyConcept(
                        title = "How Animals Communicate",
                        definition = "Animals use different methods like sounds, movements, and smells to talk to each other.",
                        keyPoints = listOf(
                            "Birds sing songs to attract mates",
                            "Dogs wag tails to show happiness",
                            "Bees dance to show where flowers are",
                            "Elephants use trunk touches to greet"
                        ),
                        examples = listOf(
                            "Lions roar to mark territory",
                            "Dolphins click and whistle",
                            "Cats purr when content"
                        ),
                        interactiveElement = InteractiveElement.Timeline(
                            listOf(
                                TimelineEvent(
                                    "Bee finds flowers",
                                    "Worker bee discovers nectar source",
                                    "Scouts search far and wide for the best flowers with lots of nectar"
                                ),
                                TimelineEvent(
                                    "Bee returns to hive",
                                    "Comes back with nectar and pollen",
                                    "The scout bee flies back home carrying precious cargo"
                                ),
                                TimelineEvent(
                                    "Waggle dance begins",
                                    "Performs special dance for other bees",
                                    "The bee moves in figure-8 pattern, waggling its body"
                                ),
                                TimelineEvent(
                                    "Other bees learn",
                                    "Hive mates understand the message",
                                    "Other worker bees learn the location and fly out to collect nectar"
                                )
                            )
                        )
                    )
                )
            )
        )
        else -> listOf(
            StudyTopic(
                title = "Animal Basics",
                icon = "🐾",
                concepts = listOf(
                    StudyConcept(
                        title = "Introduction to Animals",
                        definition = "Animals are living creatures that move around, eat food, and live in different places around the world.",
                        keyPoints = listOf(
                            "Animals need food to survive",
                            "They can move from place to place",
                            "Animals have babies to continue their species",
                            "Different animals live in different environments"
                        ),
                        examples = listOf("Dogs", "Cats", "Birds", "Fish", "Elephants")
                    )
                )
            )
        )
    }
}

fun getPhysicsStudyTopics(grade: Int): List<StudyTopic> {
    return when(grade) {
        6 -> listOf(
            StudyTopic(
                title = "Simple Machines",
                icon = "⚙️",
                concepts = listOf(
                    StudyConcept(
                        title = "What are Simple Machines?",
                        definition = "Simple machines are basic tools that make work easier by changing the direction or amount of force needed.",
                        keyPoints = listOf(
                            "Levers help lift heavy things with less effort",
                            "Pulleys change direction of force",
                            "Wheels reduce friction and make moving easier",
                            "Inclined planes make lifting easier"
                        ),
                        examples = listOf(
                            "Scissors are levers",
                            "Flagpoles use pulleys",
                            "Ramps are inclined planes",
                            "Doorknobs are wheels and axles"
                        ),
                        interactiveElement = InteractiveElement.DragDrop(
                            listOf("Scissors", "Ramp", "Pulley", "Wheel", "See-saw", "Screw"),
                            listOf("Lever", "Inclined Plane", "Pulley", "Wheel & Axle")
                        ),
                        funFacts = listOf(
                            "The human body has over 200 lever systems!",
                            "Ancient Egyptians used ramps to build pyramids",
                            "A screw is really just a twisted inclined plane"
                        )
                    ),
                    StudyConcept(
                        title = "Force and Motion",
                        definition = "Force is a push or pull that can make things move, stop, or change direction.",
                        keyPoints = listOf(
                            "Push and pull are types of forces",
                            "Friction slows down moving objects",
                            "Gravity pulls everything toward Earth",
                            "The harder you push, the faster things move"
                        ),
                        examples = listOf(
                            "Pushing a swing makes it move",
                            "Pulling a rope moves objects",
                            "Balls roll slower on rough surfaces due to friction"
                        ),
                        interactiveElement = InteractiveElement.Comparison(
                            mapOf(
                                "Push" to listOf("Away from you", "Opening doors", "Moving chairs", "Throwing balls"),
                                "Pull" to listOf("Toward you", "Closing curtains", "Tug of war", "Opening drawers")
                            )
                        )
                    )
                )
            )
        )
        7 -> listOf(
            StudyTopic(
                title = "Light and Sound",
                icon = "💡",
                concepts = listOf(
                    StudyConcept(
                        title = "Properties of Light",
                        definition = "Light is energy that helps us see things. It travels very fast and can be reflected, refracted, or absorbed.",
                        keyPoints = listOf(
                            "Light travels in straight lines",
                            "Mirrors reflect light back to us",
                            "White light contains all colors",
                            "We see objects because light bounces off them"
                        ),
                        examples = listOf(
                            "Rainbows show all colors in white light",
                            "Mirrors in bathrooms reflect our image",
                            "Shadows form when light is blocked"
                        ),
                        interactiveElement = InteractiveElement.Timeline(
                            listOf(
                                TimelineEvent(
                                    "Light source shines",
                                    "Sun or bulb produces light",
                                    "Energy is released as bright light rays in all directions"
                                ),
                                TimelineEvent(
                                    "Light hits object",
                                    "Light rays reach a surface",
                                    "Some light bounces off, some gets absorbed by the object"
                                ),
                                TimelineEvent(
                                    "Light reflects to eyes",
                                    "Bounced light reaches our eyes",
                                    "Our eyes detect the reflected light rays"
                                ),
                                TimelineEvent(
                                    "Brain processes image",
                                    "We see the object clearly",
                                    "Our brain interprets the light signals as shapes and colors"
                                )
                            )
                        )
                    )
                )
            )
        )
        else -> listOf(
            StudyTopic(
                title = "Physics Basics",
                icon = "⚗️",
                concepts = listOf(
                    StudyConcept(
                        title = "Introduction to Physics",
                        definition = "Physics is the study of how things move, why things happen, and the forces around us in everyday life.",
                        keyPoints = listOf(
                            "Physics explains how things work",
                            "Forces make objects move or stop",
                            "Energy is needed to do work",
                            "Physics is everywhere in our daily life"
                        ),
                        examples = listOf("Riding bicycles", "Playing with balls", "Using see-saws", "Flying kites")
                    )
                )
            )
        )
    }
}
