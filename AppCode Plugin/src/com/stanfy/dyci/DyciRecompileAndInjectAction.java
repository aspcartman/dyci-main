package com.stanfy.dyci;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.cidr.execution.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import com.jetbrains.cidr.execution.deviceSupport.AMDevice;
import com.jetbrains.cidr.execution.deviceSupport.AMDeviceException;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created with IntelliJ IDEA.
 * User: paultaykalo
 * Date: 3/5/13
 * Time: 8:06 AM
 * LLC Stanfy, All Rights Reserved.
 */
public class DyciRecompileAndInjectAction extends AnAction {
	private Project project;
	private Logger logger;
	private String dyciPath = System.getProperty("user.home") + "/.dyci";

	private class BinaryInfo {
		String bundleIdentifier;
		String codeSignature;
	}

	@Override
	public void actionPerformed(final AnActionEvent actionEvent) {
		try {
			setup(actionEvent);

			String updatedFile = getCurrentFilePath(actionEvent);
			BuildConfiguration configuration = getBuildConfiguration();
			BinaryInfo binaryInfo = getBinaryInfo(configuration.getExecutableFilePath());

			runCompileScript(updatedFile, binaryInfo.codeSignature);
			sendDataToDevice(configuration.getDestination().getDevice(), binaryInfo.bundleIdentifier);
		} catch (Exception e) {
			logger.error(e);
			throw e;
		}


		// Check if current virtual file is not null
	}

	private void setup(AnActionEvent actionEvent) {
		project = actionEvent.getProject();
		logger = getLogger();
		if (project == null) {
			throw new RuntimeException("No project");
		}
		FileDocumentManager.getInstance().saveAllDocuments();
	}

	@NotNull
	private String getCurrentFilePath(AnActionEvent actionEvent) {
		VirtualFile currentFile = actionEvent.getData(PlatformDataKeys.VIRTUAL_FILE);
		if (currentFile == null || currentFile.getCanonicalPath() == null) {
			throw new RuntimeException("Current file is not acceptable");
		}

		return currentFile.getCanonicalPath();
	}

	@NotNull
	private BuildConfiguration getBuildConfiguration() {
		BuildConfigurationManager manager = project.getComponent(BuildConfigurationManager.class);
		BuildConfiguration selectedConfiguration = manager.getSelectedConfiguration();
		if (selectedConfiguration == null) {
			throw new RuntimeException("No build configuration selected");
		}
		return selectedConfiguration;
	}


	@NotNull
	private BinaryInfo getBinaryInfo(String binaryPath) {
		String commands[] = {"codesign", "-d", "-vvvv", binaryPath};
		BinaryInfo binaryInfo = new BinaryInfo();

		try {
			Process proc = Runtime.getRuntime().exec(commands);
			int exitCode = proc.waitFor();

			// For the reason unknown codesign writes to stdErr
			BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			String s;
			while ((s = stdError.readLine()) != null) {
				if (s.startsWith("Identifier=")) {
					binaryInfo.bundleIdentifier = s.substring("Identifier=".length(), s.length());
				}
				if (s.startsWith("Authority=") && s.indexOf('(') != -1) {
					binaryInfo.codeSignature =  s.substring(s.length() - 11, s.length() - 1);
				}
			}

			if (exitCode != 0 || binaryInfo.bundleIdentifier == null || binaryInfo.codeSignature == null) {
				throw new RuntimeException("Error getting codesign\n");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return binaryInfo;
	}

	private void runCompileScript(String filePath, String signature) {
		final String dyciScriptLocation = dyciPath + "/scripts/dyci-recompile.py";
		final File dyciScriptLocationFile = new File(dyciScriptLocation);
		logger.info("Dyci file location is " + dyciScriptLocationFile.getAbsolutePath());

		if (!dyciScriptLocationFile.exists()) {
			logger.error("Cannot run injection. No Dyci scripts were found. Make sure, that you've ran install.sh");
			return;
		}

		String[] commands = new String[]{dyciScriptLocation, filePath, signature};
		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(commands);
			int exitCode = proc.waitFor();

			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

			// read the output from the command
			StringBuilder standardOutput = new StringBuilder();
			StringBuilder errorOutput = new StringBuilder();
			String s;
			while ((s = stdInput.readLine()) != null) {
				standardOutput.append(s);
				standardOutput.append('\n');
			}

			// read any errors from the attempted command
			while ((s = stdError.readLine()) != null) {
				errorOutput.append(s);
				errorOutput.append('\n');
			}

			// All is OK!
			if (exitCode == 0) {
				logger.info("File " + filePath + " was successfully compiled\n" + standardOutput.toString() + errorOutput.toString());
			} else {
				throw new RuntimeException("File " + filePath + " was not compiled successfully\n" + standardOutput.toString() + errorOutput.toString());
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void sendDataToDevice(AMDevice device, String bundleIdentifier) {
		try {
			device.disconnect();
			device.connect();
			device.transferDirectoryToApplicationSandbox(dyciPath + "/dyci", "tmp", bundleIdentifier);
		} catch (AMDeviceException e) {
			e.printStackTrace();
			System.err.println(e);
			logger.error("Failed sending the injection file to the device", e);
		}

		logger.info("Sent new injection file to the device");

	}


	private Logger getLogger() {
		return new Logger() {
			XDebugSession currentSession = XDebuggerManager.getInstance(project).getCurrentSession();
			Logger logger = Logger.getInstance(DyciRecompileAndInjectAction.class);

			@Override
			public boolean isDebugEnabled() {
				return false;
			}

			@Override
			public void debug(@NonNls String s) {
				if (currentSession.getConsoleView() != null) {
					currentSession.getConsoleView().print(s + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
				}
				logger.debug(s);
			}

			@Override
			public void debug(@Nullable Throwable throwable) {
				if (currentSession.getConsoleView() != null && throwable != null) {
					currentSession.getConsoleView().print(throwable.toString() + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
				}
				logger.debug(throwable);
			}

			@Override
			public void debug(@NonNls String s, @Nullable Throwable throwable) {
				if (throwable != null) {
					s = s + throwable.toString();
				}
				if (currentSession.getConsoleView() != null) {
					currentSession.getConsoleView().print(s + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
				}
				logger.debug(s, throwable);
			}

			@Override
			public void info(@NonNls String s) {
				if (currentSession.getConsoleView() != null) {
					currentSession.getConsoleView().print(s + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
				}
				logger.info(s);
			}

			@Override
			public void info(@NonNls String s, @Nullable Throwable throwable) {
				if (throwable != null) {
					s = s + throwable.toString();
				}
				if (currentSession.getConsoleView() != null) {
					currentSession.getConsoleView().print(s + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
				}
				logger.info(s, throwable);
			}

			@Override
			public void warn(@NonNls String s, @Nullable Throwable throwable) {
				if (throwable != null) {
					s = s + throwable.toString();
				}
				if (currentSession.getConsoleView() != null) {
					currentSession.getConsoleView().print(s + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
				}
				logger.warn(s, throwable);
			}

			@Override
			public void error(@NonNls String s, @Nullable Throwable throwable, @NonNls @NotNull String... strings) {
				if (throwable != null) {
					s = s + throwable.toString();
				}
				for (String str : strings) {
					s = s + str;
				}
				if (currentSession.getConsoleView() != null) {
					currentSession.getConsoleView().print(s + "\n", ConsoleViewContentType.ERROR_OUTPUT);
				}
				logger.error(s, throwable, strings);
			}

			@Override
			public void setLevel(Level level) {

			}

		};
	}

}
