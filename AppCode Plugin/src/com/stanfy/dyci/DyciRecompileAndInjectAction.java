package com.stanfy.dyci;

import com.intellij.execution.impl.ProjectRunConfigurationManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.jetbrains.cidr.execution.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.xml.xpath.*;

import com.jetbrains.cidr.lang.search.AppCodeProjectScopeBuilder;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;

/**
 * Created with IntelliJ IDEA.
 * User: paultaykalo
 * Date: 3/5/13
 * Time: 8:06 AM
 * LLC Stanfy, All Rights Reserved.
 */
public class DyciRecompileAndInjectAction extends AnAction {

	static final Logger LOG = Logger.getInstance(DyciRecompileAndInjectAction.class);

	@Override
	public void actionPerformed(final AnActionEvent actionEvent) {
		FileDocumentManager.getInstance().saveAllDocuments();
		VirtualFile currentFile = actionEvent.getData(PlatformDataKeys.VIRTUAL_FILE);

		// Check if current virtual file is not null
		if (currentFile == null) {
			this.showMessageBubble(actionEvent, MessageType.ERROR, "Cannot run injection. Incorrect file specified");
			return;
		}

		String path = currentFile.getCanonicalPath();

		// Injection
		this.injectFile(actionEvent, path);
	}

	private void injectFile(final AnActionEvent actionEvent, final String path) {
		final String home = System.getProperty("user.home");
		final String dyciHome = home + "/.dyci";

		runCompileScript(actionEvent, dyciHome, path);
		sendDataToDevices(actionEvent, dyciHome);
	}

	private void runCompileScript(AnActionEvent actionEvent, String dyciHome, String path) {
		final String dyciScriptLocation = dyciHome + "/scripts/dyci-recompile.py";
		final File dyciScriptLocationFile = new File(dyciScriptLocation);
		LOG.info("Dyci file location is " + dyciScriptLocationFile.getAbsolutePath());

		if (!dyciScriptLocationFile.exists()) {
			this.showMessageBubble(actionEvent, MessageType.ERROR, "Cannot run injection. No Dyci scripts were found. Make sure, that you've ran install.sh");
			return;
		}

		String appcodeOptionsFilename = System.getProperty("user.home") + "/Library/Preferences/appCode30/options/other.xml";

		String[] commands;
		try {
			FileReader appcodeOptions = new FileReader(appcodeOptionsFilename);
			String xcodePath = xcodePath(actionEvent, appcodeOptions);
			commands = new String[]{dyciScriptLocation, path, xcodePath};
		} catch (IOException e) {
			// If the file cannot be found, it might not be a problem (user has appcode 2 or maybe the setting wasn't saved)
			// We'll just leave it up to the dyci script to find Xcode.
			commands = new String[]{dyciScriptLocation, path};
		}

		Runtime rt = Runtime.getRuntime();
		try {
			Process proc = rt.exec(commands);

			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

			// read the output from the command
			StringBuilder standardOutput = new StringBuilder();
			StringBuilder errorOutput = new StringBuilder();
			String s;
			while ((s = stdInput.readLine()) != null) {
				standardOutput.append(s);
			}

			// read any errors from the attempted command
			while ((s = stdError.readLine()) != null) {
				errorOutput.append(s);
			}

			// All is OK!
			if (proc.exitValue() == 0) {
				this.showMessageBubble(actionEvent, MessageType.INFO, "File " + path + " was successfully injected\n" + standardOutput.toString());
			} else {
				this.showMessageBubble(actionEvent, MessageType.ERROR, "File " + path + " was not injected successfully\n" + errorOutput.toString());
			}
		} catch (IOException e) {
			LOG.error("Exception on script run : " + e.getMessage());
			this.showMessageBubble(actionEvent, MessageType.ERROR, "Failed to run injection script");
		}
	}

	private void sendDataToDevices(AnActionEvent actionEvent, String dyciHome) {
		Project project = actionEvent.getProject();
		if (project == null) {
			LOG.error("No project");
			return;
		}
		XDebuggerManager debuggerManager = XDebuggerManager.getInstance(project);
		XDebugSession currentSession = debuggerManager.getCurrentSession();
		if (currentSession == null){
			LOG.error("No current debbuging session running");
			return;
		}

		currentSession.pause();
		while (!currentSession.isPaused()) {
			waitABit();
		}

		XDebuggerEvaluator evaluator = currentSession.getDebugProcess().getEvaluator();
		if (evaluator == null) {
			LOG.error("No evaluator");
			return;
		}

		evaluator.evaluate("123+345", new XDebuggerEvaluator.XEvaluationCallback() {
			@Override
			public void evaluated(@NotNull XValue xValue) {
				LOG.debug("Evaluated", xValue);
			}

			@Override
			public void errorOccurred(@NotNull String s) {
				LOG.error("Evaluation failed", s);
			}
		}, null);
	}

	private void waitABit() {
		try {
			Thread.sleep(1000);                 //1000 milliseconds is one second.
		} catch(InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private String xcodePath(final AnActionEvent actionEvent, java.io.FileReader fileReader) {
		String xpathExpression = "/application/component[@name='XcodeSettings']/option[@name='selectedXcode']/@value";
		String result = null;

		XPathFactory xpathFactory = XPathFactory.newInstance();
		XPath xpath = xpathFactory.newXPath();
		try {
			XPathExpression expr = xpath.compile(xpathExpression);
			InputSource inputSource = new InputSource(fileReader);
			result = expr.evaluate(inputSource);
		} catch (Exception e) {
			LOG.error("Exception getting Xcode path: " + e.getMessage());
			this.showMessageBubble(actionEvent, MessageType.ERROR, "Failed to run injection script");
		}

		return result;
	}

	/**
	 * Shows Error bubble
	 *
	 * @param actionEvent was passed via action Performed
	 * @param messageType type of balloon
	 * @param message     that will be shown
	 */
	private void showMessageBubble(final AnActionEvent actionEvent, final MessageType messageType, final String message) {
		StatusBar statusBar = WindowManager.getInstance().getStatusBar(actionEvent.getData(PlatformDataKeys.PROJECT));
		JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType, null).setFadeoutTime(7500).createBalloon().show(RelativePoint.getCenterOf(statusBar.getComponent()), Balloon.Position.atRight);
	}
}
