/*******************************************************************************
 * Copyright (c) 2017 QNX Software Systems and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.cdt.cmake.ui.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.cmake.core.CMakeBuildConfiguration;
import org.eclipse.cdt.cmake.core.CMakeBuildConfigurationProvider;
import org.eclipse.cdt.core.build.ICBuildConfiguration;
import org.eclipse.cdt.launch.ui.corebuild.CommonBuildTab;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class CMakeBuildTab extends CommonBuildTab {

	/**
	 * Checkbox allowing user to choose these settings over the default operating system defaults.
	 * This is connected to the CMakeBuildConfiguration.CMAKE_USE_UI_OVERRIDES preference.
	 */
	private Button useUiCmakeSettings;
	private Button unixGenButton;
	private Button ninjaGenButton;
	private Text cmakeArgsText;
	private Text buildCommandText;
	private Text cleanCommandText;
	private Label generatorLabel;
	private Label cmakeArgsLabel;
	private Label buildCommandLabel;
	private Label cleanCommandLabel;

	@Override
	protected String getBuildConfigProviderId() {
		return CMakeBuildConfigurationProvider.ID;
	}

	@Override
	public void createControl(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout());
		setControl(comp);

		Control tcControl = createToolchainSelector(comp);
		tcControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		Group cmakeGroup = new Group(comp, SWT.NONE);
		cmakeGroup.setText(Messages.CMakeBuildTab_Settings);
		cmakeGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		cmakeGroup.setLayout(new GridLayout());

		useUiCmakeSettings = new Button(cmakeGroup, SWT.CHECK);
		useUiCmakeSettings.setText(Messages.CMakeBuildTab_useUICmakeSettings);
		useUiCmakeSettings.setToolTipText(Messages.CMakeBuildTab_useUICmakeSettingsTip);
		useUiCmakeSettings.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateEnablement();
				updateLaunchConfigurationDialog();
			}
		});

		generatorLabel = new Label(cmakeGroup, SWT.NONE);
		generatorLabel.setText(Messages.CMakeBuildTab_Generator);

		Composite genComp = new Composite(cmakeGroup, SWT.BORDER);
		genComp.setLayout(new GridLayout(2, true));

		unixGenButton = new Button(genComp, SWT.RADIO);
		unixGenButton.setText(Messages.CMakeBuildTab_UnixMakefiles);
		unixGenButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateLaunchConfigurationDialog();
			}
		});

		ninjaGenButton = new Button(genComp, SWT.RADIO);
		ninjaGenButton.setText(Messages.CMakeBuildTab_Ninja);
		ninjaGenButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateLaunchConfigurationDialog();
			}
		});

		cmakeArgsLabel = new Label(cmakeGroup, SWT.NONE);
		cmakeArgsLabel.setText(Messages.CMakeBuildTab_CMakeArgs);

		cmakeArgsText = new Text(cmakeGroup, SWT.BORDER);
		cmakeArgsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		cmakeArgsText.addModifyListener(e -> updateLaunchConfigurationDialog());

		buildCommandLabel = new Label(cmakeGroup, SWT.NONE);
		buildCommandLabel.setText(Messages.CMakeBuildTab_BuildCommand);

		buildCommandText = new Text(cmakeGroup, SWT.BORDER);
		buildCommandText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		buildCommandText.addModifyListener(e -> updateLaunchConfigurationDialog());

		cleanCommandLabel = new Label(cmakeGroup, SWT.NONE);
		cleanCommandLabel.setText(Messages.CMakeBuildTab_CleanCommand);

		cleanCommandText = new Text(cmakeGroup, SWT.BORDER);
		cleanCommandText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		cleanCommandText.addModifyListener(e -> updateLaunchConfigurationDialog());
	}

	/**
	 * Updates the enabled state of the CMake settings controls based on useUiCmakeSettings checkbox
	 */
	private void updateEnablement() {
		boolean isSelected = useUiCmakeSettings.getSelection();
		generatorLabel.setEnabled(isSelected);
		unixGenButton.setEnabled(isSelected);
		ninjaGenButton.setEnabled(isSelected);
		cmakeArgsLabel.setEnabled(isSelected);
		cmakeArgsText.setEnabled(isSelected);
		buildCommandLabel.setEnabled(isSelected);
		buildCommandText.setEnabled(isSelected);
		cleanCommandLabel.setEnabled(isSelected);
		cleanCommandText.setEnabled(isSelected);
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		// Set defaults for Build Settings
		ICBuildConfiguration buildConfig = getBuildConfiguration();
		buildConfig.setProperty(CMakeBuildConfiguration.CMAKE_USE_UI_OVERRIDES,
				Boolean.toString(CMakeBuildConfiguration.CMAKE_USE_UI_OVERRIDES_DEFAULT));
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		super.initializeFrom(configuration);

		ICBuildConfiguration buildConfig = getBuildConfiguration();

		String generator = buildConfig.getProperty(CMakeBuildConfiguration.CMAKE_GENERATOR);
		updateGeneratorButtons(generator);

		String cmakeArgs = buildConfig.getProperty(CMakeBuildConfiguration.CMAKE_ARGUMENTS);
		if (cmakeArgs != null) {
			cmakeArgsText.setText(cmakeArgs);
		} else {
			cmakeArgsText.setText(""); //$NON-NLS-1$
		}

		String buildCommand = buildConfig.getProperty(CMakeBuildConfiguration.BUILD_COMMAND);
		if (buildCommand != null) {
			buildCommandText.setText(buildCommand);
		} else {
			buildCommandText.setText(""); //$NON-NLS-1$
		}

		String cleanCommand = buildConfig.getProperty(CMakeBuildConfiguration.CLEAN_COMMAND);
		if (cleanCommand != null) {
			cleanCommandText.setText(cleanCommand);
		} else {
			cleanCommandText.setText(""); //$NON-NLS-1$
		}

		boolean isSelected = Boolean.valueOf(buildConfig.getProperty(CMakeBuildConfiguration.CMAKE_USE_UI_OVERRIDES));
		useUiCmakeSettings.setSelection(isSelected);
		updateEnablement();
	}

	private void updateGeneratorButtons(String generator) {
		if (generator == null || generator.equals("Ninja")) { //$NON-NLS-1$
			ninjaGenButton.setSelection(true);
		} else {
			unixGenButton.setSelection(true);
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		super.performApply(configuration);

		ICBuildConfiguration buildConfig = getBuildConfiguration();

		String gen = ninjaGenButton.getSelection() ? "Ninja" : "Unix Makefiles"; //$NON-NLS-1$ //$NON-NLS-2$
		buildConfig.setProperty(CMakeBuildConfiguration.CMAKE_GENERATOR, gen);

		String cmakeArgs = cmakeArgsText.getText().trim();
		if (!cmakeArgs.isEmpty()) {
			buildConfig.setProperty(CMakeBuildConfiguration.CMAKE_ARGUMENTS, cmakeArgs);
		} else {
			buildConfig.removeProperty(CMakeBuildConfiguration.CMAKE_ARGUMENTS);
		}

		String buildCommand = buildCommandText.getText().trim();
		if (!buildCommand.isEmpty()) {
			buildConfig.setProperty(CMakeBuildConfiguration.BUILD_COMMAND, buildCommand);
		} else {
			buildConfig.removeProperty(CMakeBuildConfiguration.BUILD_COMMAND);
		}

		String cleanCommand = cleanCommandText.getText().trim();
		if (!cleanCommand.isEmpty()) {
			buildConfig.setProperty(CMakeBuildConfiguration.CLEAN_COMMAND, cleanCommand);
		} else {
			buildConfig.removeProperty(CMakeBuildConfiguration.CLEAN_COMMAND);
		}

		boolean isSelected = useUiCmakeSettings.getSelection();
		buildConfig.setProperty(CMakeBuildConfiguration.CMAKE_USE_UI_OVERRIDES, Boolean.toString(isSelected));

		Map<String, String> saved = new HashMap<>();
		saved.put(CMakeBuildConfiguration.CMAKE_USE_UI_OVERRIDES, Boolean.toString(isSelected));
		getBuildConfiguration().setProperties(saved);
	}

	@Override
	protected void saveProperties(Map<String, String> properties) {
		super.saveProperties(properties);
		properties.put(CMakeBuildConfiguration.CMAKE_GENERATOR,
				ninjaGenButton.getSelection() ? "Ninja" : "Unix Makefiles"); //$NON-NLS-1$ //$NON-NLS-2$

		properties.put(CMakeBuildConfiguration.CMAKE_ARGUMENTS, cmakeArgsText.getText().trim());
		properties.put(CMakeBuildConfiguration.BUILD_COMMAND, buildCommandText.getText().trim());
		properties.put(CMakeBuildConfiguration.CLEAN_COMMAND, cleanCommandText.getText().trim());
	}

	@Override
	protected void restoreProperties(Map<String, String> properties) {
		super.restoreProperties(properties);

		String gen = properties.get(CMakeBuildConfiguration.CMAKE_GENERATOR);
		if (gen != null) {
			switch (gen) {
			case "Ninja": //$NON-NLS-1$
				ninjaGenButton.setSelection(true);
				unixGenButton.setSelection(false);
				break;
			case "Unix Makefiles": //$NON-NLS-1$
				ninjaGenButton.setSelection(false);
				unixGenButton.setSelection(true);
				break;
			}
		}

		String cmakeArgs = properties.get(CMakeBuildConfiguration.CMAKE_ARGUMENTS);
		if (cmakeArgs != null) {
			cmakeArgsText.setText(cmakeArgs);
		} else {
			cmakeArgsText.setText(""); //$NON-NLS-1$
		}

		String buildCmd = properties.get(CMakeBuildConfiguration.BUILD_COMMAND);
		if (buildCmd != null) {
			buildCommandText.setText(buildCmd);
		} else {
			buildCommandText.setText(""); //$NON-NLS-1$
		}

		String cleanCmd = properties.get(CMakeBuildConfiguration.CLEAN_COMMAND);
		if (cleanCmd != null) {
			cleanCommandText.setText(cleanCmd);
		} else {
			cleanCommandText.setText(""); //$NON-NLS-1$
		}
	}

	@Override
	public String getName() {
		return Messages.CMakeBuildTab_Cmake;
	}

}
