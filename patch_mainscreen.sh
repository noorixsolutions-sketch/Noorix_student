#!/bin/bash
sed -i 's/val currentTab by viewModel.currentTab.collectAsState()/val currentTab by viewModel.currentTab.collectAsState()\n    val onboarding by viewModel.onboardingState.collectAsState()/' app/src/main/java/com/example/presentation/MainScreen.kt

# Replace bottomBar logic
cat << 'REPLACE' > tmp_bottombar.txt
        bottomBar = {
            if (currentTab != AppNavigationTab.ONBOARDING) {
                NavigationBar(
                    containerColor = NearBlackSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .border(BorderStroke(1.dp, SubtleBorder))
                ) {
                    navDestinations.forEach { dest ->
                        val isSelected = currentTab == dest.tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.setTab(dest.tab) },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = dest.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = dest.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealAccent,
                                unselectedIconColor = TextSecondary,
                                selectedTextColor = TealAccent,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = TealAccentSubtle
                            )
                        )
                    }
                }
            }
        }
REPLACE

# Replace Scaffold bottomBar block
perl -0777 -pi -e 's/bottomBar = \{\s*NavigationBar\([\s\S]*?\{\s*navDestinations\.forEach[\s\S]*?\}\s*\}\s*\}/`cat tmp_bottombar.txt`/e' app/src/main/java/com/example/presentation/MainScreen.kt

# Hide top bar too if onboarding
cat << 'REPLACE2' > tmp_topbar.txt
        topBar = {
            if (currentTab != AppNavigationTab.ONBOARDING) {
                NoorixTopBar(
                    onSystemHudClick = { viewModel.setTab(AppNavigationTab.AUDIT) }, 
                    onProfileClick = { viewModel.setTab(AppNavigationTab.AUDIT) }
                )
            }
        },
REPLACE2

perl -0777 -pi -e 's/topBar = \{\s*NoorixTopBar\([\s\S]*?\)\s*\},/`cat tmp_topbar.txt`/e' app/src/main/java/com/example/presentation/MainScreen.kt

sed -i '/AppNavigationTab.DASHBOARD -> DashboardScreen(viewModel)/i\
                AppNavigationTab.ONBOARDING -> OnboardingScreen(viewModel)' app/src/main/java/com/example/presentation/MainScreen.kt

