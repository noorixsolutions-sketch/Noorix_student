#!/bin/bash
cat << 'REPLACE' > tmp_topbar.txt
        topBar = {
            if (currentTab != AppNavigationTab.ONBOARDING) {
                NoorixTopBar(
                    onSystemHudClick = { viewModel.setTab(AppNavigationTab.AUDIT) }, 
                    onProfileClick = { viewModel.setTab(AppNavigationTab.AUDIT) }
                )
            }
        },
REPLACE

perl -0777 -pi -e 's/topBar = \{\s*if \(currentTab != AppNavigationTab\.ONBOARDING\) \{\s*NoorixTopBar\([\s\S]*?\)\s*\}\s*\}, \/\/ Fold sync\/audit into settings equivalent\s*onProfileClick = \{\s*viewModel\.setTab\(AppNavigationTab\.AUDIT\)\s*\}\s*\)\s*\}/`cat tmp_topbar.txt`/e' app/src/main/java/com/example/presentation/MainScreen.kt

