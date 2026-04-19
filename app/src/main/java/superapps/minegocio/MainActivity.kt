package superapps.minegocio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import superapps.minegocio.navigation.ProfileNavRoutes
import superapps.minegocio.ui.auth.AuthViewModel
import superapps.minegocio.ui.categoriesscreen.CategoriesScreen
import superapps.minegocio.ui.dashboardscreen.DashboardScreen
import superapps.minegocio.ui.employeesscreen.EmployeesScreen
import superapps.minegocio.ui.profile.ProfileScreen
import superapps.minegocio.ui.productsscreen.ProductsScreen
import superapps.minegocio.ui.reportsscreen.ReportsScreen
import superapps.minegocio.ui.salesscreen.SalesScreen
import superapps.minegocio.ui.warehousesscreen.WarehousesScreen
import superapps.minegocio.ui.workspacesession.WorkspaceSelectionStore
import superapps.minegocio.ui.workspacesession.WorkspaceSessionViewModel
import superapps.minegocio.ui.theme.MyApplicationTheme

private fun appDestinationFromSavedName(name: String?): AppDestinations {
    if (name.isNullOrBlank()) return AppDestinations.DASHBOARD
    if (name == "HOME") return AppDestinations.DASHBOARD
    return runCatching { AppDestinations.valueOf(name) }.getOrElse { AppDestinations.DASHBOARD }
}

private fun isInviteError(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    return message.contains("invite", ignoreCase = true) ||
        message.contains("code", ignoreCase = true)
}

class MainActivity : ComponentActivity() {
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MyApplicationApp()
            }
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasNotificationPermission) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@PreviewScreenSizes
@Composable
fun MyApplicationApp() {
    var savedDestinationName by rememberSaveable { mutableStateOf(AppDestinations.DASHBOARD.name) }
    val currentDestination = appDestinationFromSavedName(savedDestinationName)
    val authViewModel: AuthViewModel = viewModel()
    val workspaceSessionViewModel: WorkspaceSessionViewModel = viewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val workspaceSessionUiState by workspaceSessionViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val webClientId = context.getString(R.string.default_web_client_id)
    val hasValidWebClientId = webClientId.isNotBlank() && webClientId != "default_web_client_id"
    val googleSignInClient = remember(webClientId) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        val result = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
        runCatching {
            result.getResult(ApiException::class.java)
        }.onSuccess { account ->
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                authViewModel.setError(context.getString(R.string.auth_google_missing_id_token))
            } else {
                authViewModel.signInWithGoogleIdToken(idToken)
            }
        }.onFailure { error ->
            if (error is ApiException && error.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                authViewModel.clearError()
                return@onFailure
            }
            authViewModel.setError(context.getString(R.string.auth_google_sign_in_failed))
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.bootstrapAnonymousSession()
    }

    LaunchedEffect(authUiState.uid) {
        if (!authUiState.uid.isNullOrBlank()) {
            workspaceSessionViewModel.refresh()
        } else {
            WorkspaceSelectionStore.selectedWorkspaceId = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, authUiState.uid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !authUiState.uid.isNullOrBlank()) {
                workspaceSessionViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var lastWorkspaceId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(workspaceSessionUiState.selectedWorkspaceId) {
        val selectedWorkspaceId = workspaceSessionUiState.selectedWorkspaceId
        if (
            !lastWorkspaceId.isNullOrBlank() &&
            !selectedWorkspaceId.isNullOrBlank() &&
            lastWorkspaceId != selectedWorkspaceId
        ) {
            Toast.makeText(
                context,
                "Workspace changed, reloading data",
                Toast.LENGTH_SHORT,
            ).show()
        }
        lastWorkspaceId = selectedWorkspaceId
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { savedDestinationName = it.name }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestinations.DASHBOARD -> {
                DashboardScreen(
                    onOpenProfile = { savedDestinationName = AppDestinations.PROFILE.name },
                )
            }
            AppDestinations.SALES -> {
                SalesScreen(
                    onNavigateUp = { savedDestinationName = AppDestinations.DASHBOARD.name },
                )
            }
            AppDestinations.REPORTS -> {
                ReportsScreen()
            }
            AppDestinations.PROFILE -> {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = ProfileNavRoutes.PROFILE_ROOT,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(ProfileNavRoutes.PROFILE_ROOT) {
                        ProfileScreen(
                            authUiState = authUiState,
                            onSignInWithGoogle = {
                                if (!hasValidWebClientId) {
                                    authViewModel.setError(context.getString(R.string.auth_google_client_not_configured))
                                } else {
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                }
                            },
                            onSignOut = { authViewModel.signOutAndContinueAnonymously() },
                            onDismissAuthError = { authViewModel.clearError() },
                            sessionErrorMessage = workspaceSessionUiState.errorMessage,
                            onDismissSessionError = { workspaceSessionViewModel.clearError() },
                            onOpenCategories = {
                                navController.navigate(ProfileNavRoutes.CATEGORIES)
                            },
                            onOpenProducts = {
                                navController.navigate(ProfileNavRoutes.PRODUCTS)
                            },
                            onOpenSales = {
                                navController.navigate(ProfileNavRoutes.SALES)
                            },
                            onOpenWarehouses = {
                                navController.navigate(ProfileNavRoutes.WAREHOUSES)
                            },
                            onOpenEmployees = {
                                navController.navigate(ProfileNavRoutes.EMPLOYEES)
                            },
                            inviteCodeError = authUiState.errorMessage.takeIf { isInviteError(it) },
                            isSubmittingInviteCode = authUiState.isLoading,
                            workspaceName = workspaceSessionUiState.selectedWorkspaceName,
                            workspaces = workspaceSessionUiState.workspaces,
                            onSubmitInviteCode = { code ->
                                authViewModel.acceptInviteCode(code) {
                                    workspaceSessionViewModel.refresh()
                                }
                            },
                            onSelectWorkspace = { workspaceId ->
                                workspaceSessionViewModel.selectWorkspace(workspaceId)
                            },
                            onClearInviteCodeError = { authViewModel.clearError() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    composable(ProfileNavRoutes.CATEGORIES) {
                        CategoriesScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                    composable(ProfileNavRoutes.PRODUCTS) {
                        ProductsScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                    composable(ProfileNavRoutes.WAREHOUSES) {
                        WarehousesScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                    composable(ProfileNavRoutes.SALES) {
                        SalesScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                    composable(ProfileNavRoutes.EMPLOYEES) {
                        EmployeesScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    SALES("Sales", Icons.Default.ShoppingCart),
    REPORTS("Reports", Icons.Default.Assessment),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Hello $name!",
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}
