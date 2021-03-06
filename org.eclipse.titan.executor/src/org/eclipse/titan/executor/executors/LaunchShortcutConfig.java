/******************************************************************************
 * Copyright (c) 2000-2016 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.titan.executor.executors;

import static org.eclipse.titan.executor.GeneralConstants.EXECUTECONFIGFILEONLAUNCH;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.executor.tabpages.hostcontrollers.HostControllersTab;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

/**
 * @author Kristof Szabados
 * */
public abstract class LaunchShortcutConfig implements ILaunchShortcut {
	protected abstract String getConfigurationId();
	protected abstract String getDialogTitle();

	/**
	 * Initializes the provided launch configuration for execution.
	 *
	 * @param configuration the configuration to initialize.
	 * @param project the project to gain data from.
	 * @param configFilePath the path of the configuration file.
	 * */
	public abstract boolean initLaunchConfiguration(final ILaunchConfigurationWorkingCopy configuration,
	                                                final IProject project, final String configFilePath);
	
	protected ILaunchConfigurationWorkingCopy getWorkingCopy(final IProject project, IFile file, final String mode) {

		try {
			final ILaunchConfigurationType configurationType = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurationType(getConfigurationId());
			final ILaunchConfiguration[] configurations = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations(configurationType);
			final List<ILaunchConfiguration> candidateConfigurations = new ArrayList<ILaunchConfiguration>();
			for (ILaunchConfiguration configuration : configurations) {
				IResource[] resources = configuration.getMappedResources();
				if (null != resources) {
					boolean found = false;
					for (IResource resource : resources) {
						if (file.equals(resource)) {
							found = true;
						}
					}
					if (found) {
						candidateConfigurations.add(configuration);
					}
				}
			}

			if (1 == candidateConfigurations.size()) {
				candidateConfigurations.get(0).launch(mode, null);
				return null;
			} else if (candidateConfigurations.size() > 1) {
				final ILabelProvider labelProvider = DebugUITools.newDebugModelPresentation();
				final ElementListSelectionDialog dialog = new ElementListSelectionDialog(null, labelProvider);
				dialog.setTitle(getDialogTitle());
				dialog.setMessage("Select existing configuration.");
				dialog.setElements(candidateConfigurations.toArray(new ILaunchConfiguration[candidateConfigurations.size()]));
				if (dialog.open() == Window.OK) {
					final ILaunchConfiguration result = (ILaunchConfiguration) dialog.getFirstResult();
					result.launch(mode, null);
					labelProvider.dispose();
					return null;
				}

				labelProvider.dispose();
			}
			// size() == 0 case: create new configuration
			final String configurationName = "new configuration (" + file.getFullPath().toString().replace("/", "__") + ")";
			final ILaunchConfigurationWorkingCopy wc = configurationType.newInstance(null, configurationName);
			wc.setMappedResources(new IResource[] {project, file});
			wc.setAttribute(EXECUTECONFIGFILEONLAUNCH, true);

			return wc;
		} catch (CoreException e) {
			ErrorReporter.logExceptionStackTrace(e);
			return null;
		}
	}


	@Override
	public final void launch(final IEditorPart editor, final String mode) {
	}

	@Override
	public final void launch(final ISelection selection, final String mode) {
		if (!(selection instanceof IStructuredSelection)) {
			return;
		}

		final Object[] selections = ((IStructuredSelection) selection).toArray();
		if (1 != selections.length) {
			return;
		}

		if (!(selections[0] instanceof IFile)) {
			ErrorReporter.logError("Config file not found"); // Is it necessary???
			return;
		}
		
		final IFile file = (IFile) selections[0];
		final IProject project = file.getProject();

		if( project == null ) {
			ErrorReporter.logError("Project file not found");
			return;
		}
		try {
			final ILaunchConfigurationWorkingCopy wc = getWorkingCopy(project,file, mode);
			if (wc == null) {
				return; //successful launch
			}

			boolean result = initLaunchConfiguration(wc, project, file.getLocation().toOSString());
			if (result) {
				result = HostControllersTab.initLaunchConfiguration(wc);
			}
			
			if (result) {
				wc.setMappedResources(new IResource[] {project, file});
				wc.setAttribute(EXECUTECONFIGFILEONLAUNCH, true);
				final ILaunchConfiguration conf = wc.doSave();
				conf.launch(mode, null);
			}
		} catch (CoreException e) {
			ErrorReporter.logExceptionStackTrace(e);
		}
	}
}
