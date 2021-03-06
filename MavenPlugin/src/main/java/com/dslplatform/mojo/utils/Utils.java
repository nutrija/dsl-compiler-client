package com.dslplatform.mojo.utils;

import com.dslplatform.compiler.client.CompileParameter;
import com.dslplatform.compiler.client.Context;
import com.dslplatform.compiler.client.Either;
import com.dslplatform.compiler.client.parameters.*;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

public class Utils {

	public static final CompileParameter[] ALL_COMPILE_PARAMETERS = {
			GrantRole.INSTANCE,
			DslCompiler.INSTANCE,
			Version.INSTANCE,
			Diff.INSTANCE,
			Settings.INSTANCE,
			PostgresConnection.INSTANCE,
			TempPath.INSTANCE,
			Targets.INSTANCE,
			Migration.INSTANCE,
			VarraySize.INSTANCE,
			JavaPath.INSTANCE,
			DisableColors.INSTANCE,
			ApplyMigration.INSTANCE,
			DslPath.INSTANCE,
			LogOutput.INSTANCE,
			Namespace.INSTANCE,
			Mono.INSTANCE,
			OracleConnection.INSTANCE,
			Prompt.INSTANCE,
			Dependencies.INSTANCE,
			Parse.INSTANCE,
			Download.INSTANCE,
			Force.INSTANCE,
			Maven.INSTANCE,
			SqlPath.INSTANCE,
			DotNet.INSTANCE,
			ScalaPath.INSTANCE
	};

	public static String resourceAbsolutePath(String resource) {
		if (resource == null) return null;

		try {
			String prefix = resource.startsWith("/") ? "" : "/";
			URL resourceUrl = Utils.class.getResource(prefix + resource);
			if (resourceUrl != null) {
				return new File(resourceUrl.toURI()).getAbsolutePath();
			}
		} catch (Exception ignore) {
		}

		File result = new File(resource);
		if (result.exists()) return result.getAbsolutePath();

		return null;
	}

	public static String createDirIfNotExists(String dir) throws MojoExecutionException {
		if (dir == null) return null;
		File file = new File(dir);
		if (!file.exists()) {
			if (!file.mkdirs()) {
				throw new MojoExecutionException("Error creating the dirs: " + file.getAbsolutePath());
			}
		}
		return file.getAbsolutePath();
	}

	public static Targets.Option targetOptionFrom(String value) {
		for (Targets.Option option : Targets.Option.values()) {
			if (option.toString().equals(value)) return option;
		}
		return null;
	}

	public static Settings.Option settingsOptionFrom(String value) {
		for (Settings.Option option : Settings.Option.values()) {
			if (option.toString().equals(value)) return option;
		}
		return null;
	}

	public static CompileParameter compileParameterFrom(String value) {
		for (CompileParameter compileParameter : ALL_COMPILE_PARAMETERS) {
			if (compileParameter.getAlias().equals(value)) return compileParameter;
		}
		return null;
	}

	public static void sanitizeDirectories(Map<CompileParameter, String> compileParametersParsed) throws MojoExecutionException {
		for (Map.Entry<CompileParameter, String> kv : compileParametersParsed.entrySet()) {
			CompileParameter cp = kv.getKey();
			String value = kv.getValue();
			if (cp instanceof TempPath) {
				compileParametersParsed.put(cp, createDirIfNotExists(value));
			}
		}
	}

	public static void cleanupParameters(Map<CompileParameter, String> compileParametersParsed) {
		// Disallow custom temp path
		Iterator<Map.Entry<CompileParameter, String>> it = compileParametersParsed.entrySet().iterator();
		while (it.hasNext()) {
			// Disallow custom temp path
			if (TempPath.INSTANCE.equals(it.next().getKey())) {
				it.remove();
			}
		}
	}

	public static void copyFolder(final File sources, final File target, final Context context) throws MojoExecutionException {
		for (final String fn : sources.list()) {
			final File sf = new File(sources, fn);
			final File tf = new File(target, fn);
			if (sf.isDirectory()) {
				if (!tf.mkdirs() && !tf.exists()) {
					String msg = "Failed to create target folder: " + tf.getAbsolutePath();
					context.error(msg);
					throw new MojoExecutionException(msg);
				}
				copyFolder(sf, tf, context);
			} else {
				final Either<String> content = com.dslplatform.compiler.client.Utils.readFile(sf);
				if (!content.isSuccess()) {
					String msg = "Error reading source file: " + sf.getAbsolutePath();
					context.error(msg);
					throw new MojoExecutionException(msg);
				}
				writeToFile(context, tf, content.get());
			}
		}
	}

	public static void writeToFile(Context context, File file, String contents) throws MojoExecutionException {
		try {
			com.dslplatform.compiler.client.Utils.saveFile(context, file, contents);
		} catch (IOException e) {
			throw new MojoExecutionException("Error writing to file: " + file.getAbsolutePath() + ", contents: " + contents, e);
		}
	}
}
