package superapps.minegocio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import superapps.minegocio.navigation.HomeNavRoutes
import superapps.minegocio.ui.auth.AuthViewModel
import superapps.minegocio.ui.categoriesscreen.CategoriesScreen
import superapps.minegocio.ui.home.HomeScreen
import superapps.minegocio.ui.productsscreen.ProductsScreen
import superapps.minegocio.ui.reportsscreen.ReportsScreen
import superapps.minegocio.ui.salesscreen.SalesScreen
import superapps.minegocio.ui.warehousesscreen.WarehousesScreen
import superapps.minegocio.ui.theme.MyApplicationTheme

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
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val authViewModel: AuthViewModel = viewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
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
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestinations.HOME -> {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = HomeNavRoutes.HOME,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(HomeNavRoutes.HOME) {
                        HomeScreen(
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
                            onOpenProducts = {
                                navController.navigate(HomeNavRoutes.PRODUCTS)
                            },
                            onOpenSales = {
                                navController.navigate(HomeNavRoutes.SALES)
                            },
                            onOpenCategories = {
                                navController.navigate(HomeNavRoutes.CATEGORIES)
                            },
                            onOpenWarehouses = {
                                navController.navigate(HomeNavRoutes.WAREHOUSES)
                            },
                        )
                    }
                    composable(HomeNavRoutes.CATEGORIES) {
                        CategoriesScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                    composable(HomeNavRoutes.PRODUCTS) {
                        ProductsScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                    composable(HomeNavRoutes.WAREHOUSES) {
                        WarehousesScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                    composable(HomeNavRoutes.SALES) {
                        SalesScreen(
                            onNavigateUp = { navController.popBackStack() },
                        )
                    }
                }
            }
            AppDestinations.SALES -> {
                SalesScreen(
                    onNavigateUp = { currentDestination = AppDestinations.HOME },
                )
            }
            AppDestinations.REPORTS -> {
                ReportsScreen()
            }
            else -> Greeting(
                name = "Android",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
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
