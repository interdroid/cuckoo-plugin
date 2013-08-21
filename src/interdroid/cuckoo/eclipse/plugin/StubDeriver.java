package interdroid.cuckoo.eclipse.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StubDeriver {

	private final ErrorReporter reporter;

	public StubDeriver(ErrorReporter reporter) {
		this.reporter = reporter;
	}

	public void deriveStubFrom(String fileName, String projectLocation) {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(
					fileName)));
		} catch (FileNotFoundException e) {
			reporter.error(fileName + ": not found.");
		}

		String packageName = null;
		String interfaceName = null;
		List<String> methods = new ArrayList<String>();
		try {
			boolean localOnly = false;
			while (true) {
				String line = in.readLine();
				if (line == null)
					break;
				if (line.startsWith("package ")) {//$NON-NLS-1$
					packageName = line.substring("package ".length(), line//$NON-NLS-1$
							.indexOf(';'));
				}
				if (line.startsWith("public interface ")) {//$NON-NLS-1$
					interfaceName = line.substring(
							"public interface ".length(), line//$NON-NLS-1$
									.indexOf(" extends"));//$NON-NLS-1$
				}
				if (line.startsWith("public ")//$NON-NLS-1$
						&& line.endsWith(" throws android.os.RemoteException;")) {//$NON-NLS-1$
					if (!localOnly) {
						methods.add(line
								.substring(
										"public ".length(), line//$NON-NLS-1$
												.indexOf(" throws android.os.RemoteException;")));//$NON-NLS-1$
					}
					localOnly = false;
				}
				if (line.trim().startsWith("//")
						&& (line.trim().substring(2).trim()
								.startsWith("cuckoo.strategy") || line.trim()
								.substring(2).trim()
								.startsWith("cuckoo:strategy"))) {
					try {
						if ("local".equals(line.trim().substring(2).trim()
								.split("=", 2)[1])) {
							localOnly = true;
						}
					} catch (Throwable t) {
						// ignore
					}
				}
				if (line.endsWith("}")) {
					localOnly = false;
				}

				if (line.startsWith("public static abstract class Stub extends android.os.Binder implements")) {//$NON-NLS-1$
				}
			}
			in.close();
		} catch (IOException e) {
			reporter.error(fileName + ": could not read.");
			return;
		} finally {
			try {
				in.close();
			} catch (Throwable e) {
				// ignored
			}
		}

		File file = new File(fileName);
		// create dir and files
		File derivedDirectory = new File(
				(file.getParent() + File.separator + "remote").replace("gen",//$NON-NLS-1$//$NON-NLS-2$
						"remote" + File.separator + packageName + ".remote."//$NON-NLS-1//$NON-NLS-2$
								+ interfaceName));
		String derivedPath = derivedDirectory.getPath();
		if (!derivedDirectory.exists()) {
			if (!derivedDirectory.mkdirs()) {
				reporter.error("unable to create derived directory: "
						+ derivedPath);
				return;
			}
		}
		File derivedInterfaceFile = new File(derivedPath + File.separator
				+ interfaceName + ".java");//$NON-NLS-1$
		writeInterface(derivedInterfaceFile, packageName + ".remote",//$NON-NLS-1$
				interfaceName, methods);
		File derivedImplementationFile = new File(derivedPath + File.separator
				+ interfaceName + "Impl.java");//$NON-NLS-1$//$NON-NLS-2$
		if (derivedImplementationFile.exists()) {
			// derivedImplementationFile.renameTo(new File(derivedPath
			//					+ File.separator + interfaceName + "Impl.java.saved"));//$NON-NLS-1$//$NON-NLS-2$
		} else {
			writeImpl(derivedImplementationFile, packageName + ".remote",//$NON-NLS-1$
					interfaceName, methods);
		}
		File derivedExternalJarDirectory = new File(projectLocation
				+ File.separator
				+ "remote"//$NON-NLS-1$
				+ File.separator + packageName
				+ ".remote." + interfaceName + File.separator + "external");//$NON-NLS-1$//$NON-NLS-2$
		if (!derivedExternalJarDirectory.exists()) {
			derivedExternalJarDirectory.mkdirs();
		}
	}

	private void writeInterface(File file, String packageName,
			String interfaceName, List<String> methods) {
		FileWriter out = null;
		try {
			out = new FileWriter(file);
			out.write("package " + packageName + ";\n");//$NON-NLS-1$//$NON-NLS-2$
			out.write("interface " + interfaceName + " {\n");//$NON-NLS-1$//$NON-NLS-2$
			for (String method : methods) {
				out.write("public " + method + " throws Exception;\n");//$NON-NLS-1$//$NON-NLS-2$
			}
			out.write("}\n");//$NON-NLS-1$
			out.flush();
		} catch (IOException e) {
			reporter.error(file.getName() + ": could not write.");
		} finally {
			try {
				out.close();
			} catch (Throwable e) {
				// ignore
			}
		}
	}

	private void writeImpl(File file, String packageName, String interfaceName,
			List<String> methods) {
		FileWriter out = null;
		try {
			out = new FileWriter(file);
			out.write("package " + packageName + ";\n");//$NON-NLS-1$//$NON-NLS-2$
			out.write("public class " + interfaceName + "Impl"//$NON-NLS-1$//$NON-NLS-2$
					// + " extends interdroid.cuckoo.CuckooService implements "//$NON-NLS-1$
					+ " extends Object implements " + packageName + "."//$NON-NLS-1$//$NON-NLS-2$
					+ interfaceName + " {\n");//$NON-NLS-1$
			for (String method : methods) {
				out.write("public " + method + " throws Exception {\n");//$NON-NLS-1$//$NON-NLS-2$
				out.write("    return " + getDefaultReturnValue(method)//$NON-NLS-1$
						+ ";\n");//$NON-NLS-1$
				out.write("}\n");//$NON-NLS-1$
			}
			out.write("}\n");//$NON-NLS-1$
			out.flush();
			out.close();
		} catch (IOException e) {
			reporter.error(file.getName() + ": could not write.");
		}
	}

	private String getDefaultReturnValue(String method) {
		if (method.startsWith("void")) {//$NON-NLS-1$
			return "";//$NON-NLS-1$
		}
		if (method.split(" ")[0].endsWith("[]")) {//$NON-NLS-1$
			return "null";//$NON-NLS-1$
		}
		if (method.startsWith("int ")) {//$NON-NLS-1$
			return "0";//$NON-NLS-1$
		}
		if (method.startsWith("boolean ")) {//$NON-NLS-1$
			return "true";//$NON-NLS-1$
		}
		if (method.startsWith("char ")) {//$NON-NLS-1$
			return "0";//$NON-NLS-1$
		}
		if (method.startsWith("long ")) {//$NON-NLS-1$
			return "0";//$NON-NLS-1$
		}
		if (method.startsWith("byte ")) {//$NON-NLS-1$
			return "0";//$NON-NLS-1$
		}
		if (method.startsWith("float ")) {//$NON-NLS-1$
			return "0";//$NON-NLS-1$
		}
		if (method.startsWith("double ")) {//$NON-NLS-1$
			return "0";//$NON-NLS-1$
		}
		if (method.startsWith("short ")) {//$NON-NLS-1$
			return "0";//$NON-NLS-1$
		}
		return "null";//$NON-NLS-1$
	}

}
