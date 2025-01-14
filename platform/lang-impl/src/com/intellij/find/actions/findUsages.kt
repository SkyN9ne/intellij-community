// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.find.actions

import com.intellij.find.FindSettings
import com.intellij.find.actions.SearchOptionsService.SearchVariant
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.buildUsageViewQuery
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.psi.impl.search.runSearch
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewContentManager
import com.intellij.usages.UsageSearcher
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

fun findUsages(showDialog: Boolean, project: Project, selectedScope: SearchScope, target: SearchTarget) {
  findUsages(showDialog, project, target, target.usageHandler, selectedScope)
}

private fun <O> findUsages(showDialog: Boolean,
                           project: Project,
                           target: SearchTarget,
                           handler: UsageHandler<O>,
                           selectedScope: SearchScope) {
  val allOptions = getSearchOptions(SearchVariant.FIND_USAGES, target, handler, selectedScope)
  if (showDialog) {
    val canReuseTab = canReuseTab(project)
    val dialog = UsageOptionsDialog(project, target.displayString, handler, allOptions, target.showScopeChooser(), canReuseTab)
    if (!dialog.showAndGet()) {
      // cancelled
      return
    }
    val dialogResult: AllSearchOptions<O> = dialog.result()
    setSearchOptions(SearchVariant.FIND_USAGES, target, dialogResult)
    findUsages(project, target, handler, dialogResult)
  }
  else {
    findUsages(project, target, handler, allOptions)
  }
}

internal fun <O> findUsages(project: Project, target: SearchTarget, handler: UsageHandler<O>, allOptions: AllSearchOptions<O>) {
  val query = buildUsageViewQuery(project, target, handler, allOptions)
  val factory = Factory {
    UsageSearcher {
      runSearch(project, query, it)
    }
  }
  val usageViewPresentation = UsageViewPresentation().apply {
    searchString = handler.getSearchString(allOptions)
    scopeText = allOptions.options.searchScope.displayName
    tabText = UsageViewBundle.message("search.title.0.in.1", searchString, scopeText)
    isOpenInNewTab = FindSettings.getInstance().isShowResultsInSeparateView || !canReuseTab(project)
  }
  project.service<UsageViewManager>().searchAndShowUsages(
    arrayOf(SearchTarget2UsageTarget(project, target, allOptions)),
    factory,
    false,
    true,
    usageViewPresentation,
    null
  )
}

private fun canReuseTab(project: Project): Boolean {
  val contentManager = UsageViewContentManager.getInstance(project)
  val selectedContent = contentManager.getSelectedContent(true)
  return if (selectedContent == null) {
    contentManager.reusableContentsCount != 0
  }
  else {
    !selectedContent.isPinned
  }
}

internal val SearchTarget.displayString: String get() = presentation.presentableText

@Nls(capitalization = Nls.Capitalization.Title)
internal fun <O> UsageHandler<O>.getSearchString(allOptions: AllSearchOptions<O>): String {
  return getSearchString(allOptions.options, allOptions.customOptions)
}

internal fun SearchTarget.showScopeChooser(): Boolean {
  return maximalSearchScope !is LocalSearchScope
}
