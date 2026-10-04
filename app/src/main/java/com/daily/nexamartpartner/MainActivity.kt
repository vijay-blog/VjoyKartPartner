package com.daily.nexamartpartner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.daily.nexamartpartner.core.permissions.FirstLaunchPermissionManager
import com.daily.nexamartpartner.databinding.ActivityMainBinding
import com.daily.nexamartpartner.di.appContainer
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModel
import com.daily.nexamartpartner.features.auth.presentation.viewmodel.AuthCoordinatorViewModelFactory
import com.daily.nexamartpartner.routing.AuthDestinationResolver
import com.daily.nexamartpartner.routing.NavigationGuard
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authCoordinatorViewModel: AuthCoordinatorViewModel
    private var isGuardRedirecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FirstLaunchPermissionManager.requestIfFirstLaunch(this)

        authCoordinatorViewModel = ViewModelProvider(
            this,
            AuthCoordinatorViewModelFactory(
                restoreSessionUseCase = applicationContext.appContainer.restoreSessionUseCase,
                logoutUseCase = applicationContext.appContainer.logoutUseCase,
                authStateStore = applicationContext.appContainer.authStateStore
            )
        )[AuthCoordinatorViewModel::class.java]

        observeAuthState()
        observeConnectivity()
        authCoordinatorViewModel.initialize()
    }

    private fun observeConnectivity() {
        val monitor = applicationContext.appContainer.networkConnectivityMonitor
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                monitor.state.collect { state ->
                    binding.offlineBanner.isVisible =
                        state == com.daily.nexamartpartner.core.network.NetworkConnectivityMonitor.State.OFFLINE
                }
            }
        }
    }

    private fun observeAuthState() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController
        installNavigationGuard(navController)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authCoordinatorViewModel.authState.collect { authState ->
                    val targetRootId = AuthDestinationResolver.resolve(authState)
                    if (!isAtOrWithinDestination(navController, targetRootId)) {
                        navigateWithRootGraphHop(navController, targetRootId)
                    }
                }
            }
        }
    }

    private fun installNavigationGuard(navController: NavController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (isGuardRedirecting) {
                isGuardRedirecting = false
                return@addOnDestinationChangedListener
            }
            val authState = authCoordinatorViewModel.authState.value
            val allowedDestination = NavigationGuard.resolveAuthorizedDestination(
                authState = authState,
                requestedDestinationId = destination.id
            )
            if (!isAtOrWithinDestination(navController, allowedDestination)) {
                isGuardRedirecting = true
                navigateWithRootGraphHop(navController, allowedDestination)
            }
        }
    }

    private fun navigateWithRootGraphHop(navController: NavController, destinationId: Int) {
        val targetParentGraphId = when (destinationId) {
            R.id.authHomeFragment,
            R.id.adminLoginFragment,
            R.id.createDeliveryAccountFragment,
            R.id.loginFragment,
            R.id.unsupportedRoleFragment -> R.id.authGraph
            else -> null
        }
        val requiresParentHop = targetParentGraphId != null &&
            !isAtOrWithinDestination(navController, targetParentGraphId)
        if (requiresParentHop) {
            navController.navigate(
                targetParentGraphId,
                null,
                navOptions {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            )
        }
        if (!isAtOrWithinDestination(navController, destinationId)) {
            navController.navigate(
                destinationId,
                null,
                navOptions {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            )
        }
    }

    private fun isAtOrWithinDestination(navController: NavController, destinationId: Int): Boolean {
        val current = navController.currentDestination ?: return false
        return current.id == destinationId || current.hierarchy.any { it.id == destinationId }
    }
}
