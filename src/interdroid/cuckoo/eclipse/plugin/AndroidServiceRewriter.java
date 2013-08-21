package interdroid.cuckoo.eclipse.plugin;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

public class AndroidServiceRewriter {

	private final ErrorReporter reporter;

	public AndroidServiceRewriter(ErrorReporter reporter) {
		this.reporter = reporter;
	}

	public void androidServiceRewrite(String file, Properties aidlProperties) {
		List<String> lines = Util.getFileAsStringList(file, reporter);

		if (lines == null) {
			return;
		}

		// skip this file if we already modified it
		if (lines.get(2).equals(" * MODIFIED BY CUCKOO")) {//$NON-NLS-1$
			reporter.error(file + ": was already rewritten.");
			return;
		}
		lines.add(2, " * MODIFIED BY CUCKOO");//$NON-NLS-1$

		String abstractGetContextMethod = "\n"//$NON-NLS-1$
				+ "/************* START INSERTED CODE *************/\n"//$NON-NLS-1$
				+ "public final android.content.Context getContext() {\n"//$NON-NLS-1$
				+ "    try {\n"//$NON-NLS-1$
				+ "        java.lang.reflect.Field field = this.getClass().getDeclaredField(\"this$0\");\n"//$NON-NLS-1$
				+ "        field.setAccessible(true);\n"//$NON-NLS-1$
				+ "        return (android.app.Service) field.get(this);\n"//$NON-NLS-1$
				+ "    } catch (Exception e) {\n"//$NON-NLS-1$
				+ "      // will never happen\n"//$NON-NLS-1$
				+ "    }\n"//$NON-NLS-1$
				+ "    return null;\n"//$NON-NLS-1$
				+ "}\n";//$NON-NLS-1$
		
		// add the final getContext method
		lines.add(
				findLine("this.attachInterface(this, DESCRIPTOR);", lines) + 2,//$NON-NLS-1$
				abstractGetContextMethod);

		// now find all the method invocations that need to be rewritten
		List<Integer> methodTransactions = findLines("case TRANSACTION_",//$NON-NLS-1$
				lines, findLine("@Override public boolean onTransact", lines));//$NON-NLS-1$

		// rewrite the onTransact method to following (add final)
		lines.set(
				findLine("@Override public boolean onTransact", lines),//$NON-NLS-1$
				"@Override public boolean onTransact(int code, android.os.Parcel data, final android.os.Parcel reply, int flags) throws android.os.RemoteException");//$NON-NLS-1$

		// rewrite all the methods (in reverse, so that we don't mess up the
		// linenumbers we just found by inserting code).
		for (int pos = methodTransactions.size() - 1; pos >= 0; pos--) {
			int startLine = methodTransactions.get(pos);
			int offset = 0;
			String serviceName = getServiceName(lines);

			List<String> parameterTypes = new ArrayList<String>();
			String line = null;
			// read until we find the line that contains "this.xxx" where "xxx"
			// is the method
			do {
				line = lines.get(startLine + offset++);
				// in the meantime we store the parameter types
				if (line.endsWith("_arg" + parameterTypes.size() + ";")) {//$NON-NLS-1$//$NON-NLS-2$
					lines.set(startLine + offset - 1, "final " + line);//$NON-NLS-1$
					parameterTypes.add(line.split(" ")[0]);//$NON-NLS-1$
				}
			} while (!(line.contains("this.") && !line.contains("this.getClass()")));//$NON-NLS-1$//$NON-NLS-2$
			// parse the method name
			String methodName = parseMethod(line);
			// now retrieve the strategy from the properties
			String strategy = aidlProperties.getProperty(methodName);
			// parse the return type
			String returnType = parseReturnType(line);
			// parse write type
			String writeType = parseWriteType(
					lines.get(startLine + offset + 1), returnType);
			// see whether the writeType is a parcelable
			boolean parcelableWriteType = isParcelable(
					lines.get(startLine + offset + 1), returnType);
			String localCode = getLocalCode(lines, startLine + offset - 1);

			String insertedMethodCode = null;
			if (strategy.equals("parallel")) {
				insertedMethodCode = "\n"//$NON-NLS-1$
						+ "/************* START INSERTED CODE *************/\n"//$NON-NLS-1$
						+ "   // cuckoo.strategy=" + strategy + "\n"//$NON-NLS-1$
						+ "   final boolean[] done = new boolean[]{false};\n"//$NON-NLS-1$
						+ "   Object lock = new Object();\n"//$NON-NLS-1$
						+ "   final float weight = weight_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final long returnSize = returnSize_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final boolean screenOn = hasScreenOn_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final long inputSize = data.dataSize();\n"//$NON-NLS-1$
						+ "   interdroid.cuckoo.client.StatusThread remote = new interdroid.cuckoo.client.StatusThread(lock) {\n"//$NON-NLS-1$
						+ "       public boolean invoke() {\n"
						+ "           try {\n"//$NON-NLS-1$//$NON-NLS-2$
						+ "               statistics.weight = weight;\n"//$NON-NLS-1$
						+ "               "//$NON-NLS-1$
						+ (returnType == null ? "" : returnType//$NON-NLS-1$
								+ " _remoteResult = (" + asObject(returnType)//$NON-NLS-1$
								+ ") ")//$NON-NLS-1$
						+ "interdroid.cuckoo.client.Cuckoo.invokeMethod(getContext(), statistics, \""//$NON-NLS-1$
						+ serviceName
						+ "\", \""//$NON-NLS-1$
						+ methodName
						+ "\", new Class<?>[] {\n"//$NON-NLS-1$
						+ getParametersAsString(parameterTypes)
						+ "               }, new boolean[] {\n"//$NON-NLS-1$
						+ getOutParametersAsString(localCode,
								parameterTypes.size())
						+ "               }, new Object[] {\n"//$NON-NLS-1$
						+ getObjectsAsString(parameterTypes.size())
						+ "               }, \"" + strategy + "\", weight, inputSize, returnSize, screenOn);\n"//$NON-NLS-1$
						+ "               synchronized (done) {\n"//$NON-NLS-1$
						+ "                   if (!done[0]) {\n"//$NON-NLS-1$
						+ "                       reply.writeNoException();\n"//$NON-NLS-1$
						+ (parcelableWriteType ? "                       if ((_remoteResult != null)) {\n"//$NON-NLS-1$
								+ "                           reply.writeInt(1);\n"//$NON-NLS-1$
								+ "                           _remoteResult.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);\n"//$NON-NLS-1$
								+ "                       } else {\n"//$NON-NLS-1$
								+ "                           reply.writeInt(0);\n"//$NON-NLS-1$
								+ "                       }\n"//$NON-NLS-1$
								: (writeType == null) ? ""//$NON-NLS-1$
										: "                       reply.write" + writeType//$NON-NLS-1$
												+ "(_remoteResult);\n")//$NON-NLS-1$
						+ "                   done[0] = true;\n"//$NON-NLS-1$
						+ "                   }\n"//$NON-NLS-1$
						+ "               }\n"//$NON-NLS-1$
						+ "               interdroid.cuckoo.client.Oracle.storeStatistics(getContext(), \"" + serviceName + "." + methodName + "\", statistics);\n"//$NON-NLS-1$						
						+ "               return true;\n"//$NON-NLS-1$
						+ getOutParameterLines(localCode)
						+ "           } catch (Exception e) {\n"//$NON-NLS-1$
						+ "               e.printStackTrace();\n"//$NON-NLS-1$
						+ "               return false;\n"//$NON-NLS-1$
						+ "           }\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   };\n"//$NON-NLS-1$
						+ "   interdroid.cuckoo.client.StatusThread local = new interdroid.cuckoo.client.StatusThread(lock) {\n"//$NON-NLS-1$
						+ "       public boolean invoke() {\n"//$NON-NLS-1$
						+ "           statistics.weight = weight;\n"//$NON-NLS-1$
						+ "           statistics.inputSize = inputSize;\n"//$NON-NLS-1$
						+ "           long start = System.currentTimeMillis();\n"//$NON-NLS-1$						
						+ protectParcel(localCode)
						+ "\n"//$NON-NLS-1$
						+ "           statistics.resource = new interdroid.cuckoo.client.Cuckoo.Resource();\n"//$NON-NLS-1$
						+ "           statistics.executionTime = System.currentTimeMillis() - start;\n"//$NON-NLS-1$
						+ "           statistics.returnSize = reply.dataSize();\n"//$NON-NLS-1$
						+ "           interdroid.cuckoo.client.Oracle.storeStatistics(getContext(), \"" + serviceName + "." + methodName + "\", statistics);\n"//$NON-NLS-1$
						+ "           return true;\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   };\n"//$NON-NLS-1$
						+ "   remote.start();\n"//$NON-NLS-1$
						+ "   local.start();\n"//$NON-NLS-1$
						+ "   synchronized (lock) {\n"//$NON-NLS-1$
						+ "       try {\n"//$NON-NLS-1$
						+ "           lock.wait();\n"//$NON-NLS-1$
						+ "       } catch (InterruptedException e) {}\n"//$NON-NLS-1$
						+ "   }\n"//$NON-NLS-1$
						+ "   // if the finished one failed, wait for the other\n"//$NON-NLS-1$
						+ "   if (remote.hasFailed() || local.hasFailed()) {\n"//$NON-NLS-1$
						+ "       synchronized (lock) {\n"//$NON-NLS-1$
						+ "           try {\n"//$NON-NLS-1$
						+ "               lock.wait();\n"//$NON-NLS-1$
						+ "           } catch (InterruptedException e) {}\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   }\n"//$NON-NLS-1$
						+ "   // stop throws an Exception, so we can't stop the thread.\n"//$NON-NLS-1$
						+ "   // if (local.isAlive()) {\n"//$NON-NLS-1$
						+ "   //    local.stop();\n"//$NON-NLS-1$
						+ "   // }\n"//$NON-NLS-1$
						+ "/*************  END INSERTED CODE  *************/\n\n";//$NON-NLS-1$
			} else if (strategy.equals("remote")) {
				insertedMethodCode = "\n"//$NON-NLS-1$
						+ "/************* START INSERTED CODE *************/\n"//$NON-NLS-1$
						+ "   // cuckoo.strategy=" + strategy + "\n"//$NON-NLS-1$
						+ "   Object lock = new Object();\n"//$NON-NLS-1$
						+ "   final float weight = weight_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final long returnSize = returnSize_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final boolean screenOn = hasScreenOn_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final long inputSize = data.dataSize();\n"//$NON-NLS-1$
						+ "   interdroid.cuckoo.client.StatusThread remote = new interdroid.cuckoo.client.StatusThread(lock) {\n"//$NON-NLS-1$
						+ "       public boolean invoke() {\n"
						+ "           try {\n"//$NON-NLS-1$//$NON-NLS-2$
						+ "               statistics.weight = weight;\n"//$NON-NLS-1$
						+ "               "//$NON-NLS-1$
						+ (returnType == null ? "" : returnType//$NON-NLS-1$
								+ " _remoteResult = (" + asObject(returnType)//$NON-NLS-1$
								+ ") ")//$NON-NLS-1$
						+ "interdroid.cuckoo.client.Cuckoo.invokeMethod(getContext(), statistics, \""//$NON-NLS-1$
						+ serviceName
						+ "\", \""//$NON-NLS-1$
						+ methodName
						+ "\", new Class<?>[] {\n"//$NON-NLS-1$
						+ getParametersAsString(parameterTypes)
						+ "               }, new boolean[] {\n"//$NON-NLS-1$
						+ getOutParametersAsString(localCode,
								parameterTypes.size())
						+ "               }, new Object[] {\n"//$NON-NLS-1$
						+ getObjectsAsString(parameterTypes.size())
						+ "               }, \"" + strategy + "\", weight, inputSize, returnSize, screenOn);\n"//$NON-NLS-1$
						+ "               reply.writeNoException();\n"//$NON-NLS-1$
						+ (parcelableWriteType ? "               if ((_remoteResult != null)) {\n"//$NON-NLS-1$
								+ "                   reply.writeInt(1);\n"//$NON-NLS-1$
								+ "                   _remoteResult.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);\n"//$NON-NLS-1$
								+ "               } else {\n"//$NON-NLS-1$
								+ "                   reply.writeInt(0);\n"//$NON-NLS-1$
								+ "               }\n"//$NON-NLS-1$
								: (writeType == null) ? ""//$NON-NLS-1$
										: "               reply.write" + writeType//$NON-NLS-1$
												+ "(_remoteResult);\n")//$NON-NLS-1$
						+ "               interdroid.cuckoo.client.Oracle.storeStatistics(getContext(), \"" + serviceName + "." + methodName + "\", statistics);\n"//$NON-NLS-1$												
						+ "               return true;\n"//$NON-NLS-1$
						+ getOutParameterLines(localCode)
						+ "           } catch (Exception e) {\n"//$NON-NLS-1$
						+ "               e.printStackTrace();\n"//$NON-NLS-1$
						+ "               return false;\n"//$NON-NLS-1$
						+ "           }\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   };\n"//$NON-NLS-1$
						+ "   interdroid.cuckoo.client.StatusThread local = new interdroid.cuckoo.client.StatusThread(lock) {\n"//$NON-NLS-1$
						+ "       public boolean invoke() {\n"//$NON-NLS-1$
						+ "           long start = System.currentTimeMillis();\n"//$NON-NLS-1$						
						+ localCode
						+ "\n"//$NON-NLS-1$
						+ "           statistics.resource = new interdroid.cuckoo.client.Cuckoo.Resource();\n"//$NON-NLS-1$
						+ "           statistics.executionTime = System.currentTimeMillis() - start;\n"//$NON-NLS-1$
						+ "           statistics.weight = 1;\n"//$NON-NLS-1$
						+ "           interdroid.cuckoo.client.Oracle.storeStatistics(getContext(), \"" + serviceName + "." + methodName + "\", statistics);\n"//$NON-NLS-1$
						+ "           return true;\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   };\n"//$NON-NLS-1$
						+ "   if (!remote.invoke()) {\n"//$NON-NLS-1$
						+ "       local.invoke();\n"//$NON-NLS-1$
						+ "   }\n" //$NON-NLS-1$
						+ "/*************  END INSERTED CODE  *************/\n\n";//$NON-NLS-1$
			} else if (strategy.equals("local")) {
				insertedMethodCode = localCode;
			} else {
				// strategy is "energy", "speed", "energy/speed", "speed/energy"
				insertedMethodCode = "\n"//$NON-NLS-1$
						+ "/************* START INSERTED CODE *************/\n"//$NON-NLS-1$
						+ "   // cuckoo.strategy=" + strategy + "\n"//$NON-NLS-1$
						+ "   final boolean[] done = new boolean[]{false};\n"//$NON-NLS-1$
						+ "   Object lock = new Object();\n"//$NON-NLS-1$
						+ "   final float weight = weight_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final long returnSize = returnSize_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final boolean screenOn = hasScreenOn_" + methodName + "(\n" + getObjectsAsString(parameterTypes.size()) + "    );\n"//$NON-NLS-1$
						+ "   final long inputSize = data.dataSize();\n"//$NON-NLS-1$
						+ "   interdroid.cuckoo.client.StatusThread remote = new interdroid.cuckoo.client.StatusThread(lock) {\n"//$NON-NLS-1$
						+ "       public boolean invoke() {\n"
						+ "           try {\n"//$NON-NLS-1$//$NON-NLS-2$
						+ "               statistics.weight = weight;\n"//$NON-NLS-1$
						+ "               "//$NON-NLS-1$
						+ (returnType == null ? "" : returnType//$NON-NLS-1$
								+ " _remoteResult = (" + asObject(returnType)//$NON-NLS-1$
								+ ") ")//$NON-NLS-1$
						+ "interdroid.cuckoo.client.Cuckoo.invokeMethod(getContext(), statistics, \""//$NON-NLS-1$
						+ serviceName
						+ "\", \""//$NON-NLS-1$
						+ methodName
						+ "\", new Class<?>[] {\n"//$NON-NLS-1$
						+ getParametersAsString(parameterTypes)
						+ "               }, new boolean[] {\n"//$NON-NLS-1$
						+ getOutParametersAsString(localCode,
								parameterTypes.size())
						+ "               }, new Object[] {\n"//$NON-NLS-1$
						+ getObjectsAsString(parameterTypes.size())
						+ "               }, \"" + strategy + "\", weight, inputSize, returnSize, screenOn);\n"//$NON-NLS-1$
						+ "               synchronized (done) {\n"//$NON-NLS-1$
						+ "                   if (!done[0]) {\n"//$NON-NLS-1$
						+ "                       reply.writeNoException();\n"//$NON-NLS-1$
						+ (parcelableWriteType ? "                       if ((_remoteResult != null)) {\n"//$NON-NLS-1$
								+ "                           reply.writeInt(1);\n"//$NON-NLS-1$
								+ "                           _remoteResult.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);\n"//$NON-NLS-1$
								+ "                       } else {\n"//$NON-NLS-1$
								+ "                           reply.writeInt(0);\n"//$NON-NLS-1$
								+ "                       }\n"//$NON-NLS-1$
								: (writeType == null) ? ""//$NON-NLS-1$
										: "                       reply.write" + writeType//$NON-NLS-1$
												+ "(_remoteResult);\n")//$NON-NLS-1$
						+ "                   done[0] = true;\n"//$NON-NLS-1$
						+ "                   }\n"//$NON-NLS-1$
						+ "               }\n"//$NON-NLS-1$
						+ "               interdroid.cuckoo.client.Oracle.storeStatistics(getContext(), \"" + serviceName + "." + methodName + "\", statistics);\n"//$NON-NLS-1$						
						+ "               return true;\n"//$NON-NLS-1$
						+ getOutParameterLines(localCode)
						+ "           } catch (Exception e) {\n"//$NON-NLS-1$
						+ "               e.printStackTrace();\n"//$NON-NLS-1$
						+ "               return false;\n"//$NON-NLS-1$
						+ "           }\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   };\n"//$NON-NLS-1$
						+ "   interdroid.cuckoo.client.StatusThread local = new interdroid.cuckoo.client.StatusThread(lock) {\n"//$NON-NLS-1$
						+ "       public boolean invoke() {\n"//$NON-NLS-1$
						+ "           statistics.weight = weight;\n"//$NON-NLS-1$
						+ "           statistics.inputSize = inputSize;\n"//$NON-NLS-1$
						+ "           long start = System.currentTimeMillis();\n"//$NON-NLS-1$						
						+ protectParcel(localCode)
						+ "\n"//$NON-NLS-1$
						+ "           statistics.resource = new interdroid.cuckoo.client.Cuckoo.Resource();\n"//$NON-NLS-1$
						+ "           statistics.executionTime = System.currentTimeMillis() - start;\n"//$NON-NLS-1$
						+ "           statistics.returnSize = reply.dataSize();\n"//$NON-NLS-1$
						+ "           interdroid.cuckoo.client.Oracle.storeStatistics(getContext(), \"" + serviceName + "." + methodName + "\", statistics);\n"//$NON-NLS-1$
						+ "           return true;\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   };\n"//$NON-NLS-1$
						+ "   if (interdroid.cuckoo.client.Oracle.emptyHistory(getContext(), \"" + serviceName + "." + methodName + "\")) {\n"//$NON-NLS-1$
						+ "       remote.start();\n"//$NON-NLS-1$
						+ "       local.start();\n"//$NON-NLS-1$
						+ "       synchronized (lock) {\n"//$NON-NLS-1$
						+ "           try {\n"//$NON-NLS-1$
						+ "               lock.wait();\n"//$NON-NLS-1$
						+ "           } catch (InterruptedException e) {}\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "       // if the finished one failed, wait for the other\n"//$NON-NLS-1$
						+ "       if (remote.hasFailed() || local.hasFailed()) {\n"//$NON-NLS-1$
						+ "           synchronized (lock) {\n"//$NON-NLS-1$
						+ "               try {\n"//$NON-NLS-1$
						+ "                   lock.wait();\n"//$NON-NLS-1$
						+ "               } catch (InterruptedException e) {}\n"//$NON-NLS-1$
						+ "           }\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   } else {\n"//$NON-NLS-1$						
						+ "       if (!remote.invoke()) {\n"//$NON-NLS-1$
						+ "           local.invoke();\n"//$NON-NLS-1$
						+ "       }\n"//$NON-NLS-1$
						+ "   }\n"//$NON-NLS-1$
						+ "/*************  END INSERTED CODE  *************/\n\n";//$NON-NLS-1$
			}

			lines.add(startLine + offset - 1, insertedMethodCode);
		}

		// now generate the weight_ and returnSize_ and hasScreenOn_ methods

		// find the position where the method declarations start
		int methodsFound = 0;
		int pos = lines.size() - 1;
		do {
			if (lines.get(--pos).startsWith("public")) {
				methodsFound++;
			}
		} while (methodsFound < methodTransactions.size());

		int insertLine = findLine("this.attachInterface(this, DESCRIPTOR);",
				lines) + 3;

		for (int i = 0; i < methodTransactions.size(); i++) {
			String[] tmp = lines.get(pos)
					.substring(0, lines.get(pos).indexOf('(')).split(" ");
			String method = tmp[tmp.length - 1];
			String returnType = lines.get(pos).split(" ")[1];
			String weightMethod = lines.get(pos)
					.replace(method, "weight_" + method)
					.replaceFirst(Pattern.quote(returnType), "float")
					.replace(';', '{')
					+ "\n" + "    return 1;\n}\n";
			String returnSizeMethod = lines.get(pos)
					.replace(method, "returnSize_" + method)
					.replaceFirst(Pattern.quote(returnType), "long")
					.replace(';', '{')
					+ "\n" + "    return 1;\n}\n";
			String screenMethod = lines.get(pos)
					.replace(method, "hasScreenOn_" + method)
					.replaceFirst(Pattern.quote(returnType), "boolean")
					.replace(';', '{')
					+ "\n" + "    return true;\n}\n";
			lines.add(insertLine, weightMethod);
			lines.add(insertLine, returnSizeMethod);
			lines.add(insertLine, screenMethod);
			pos += 3;
			while (!lines.get(++pos).startsWith("public")
					&& pos < lines.size() - 1) {
			}
		}

		lines.add(insertLine + 3 * methodTransactions.size(),
				"/*************  END INSERTED CODE  *************/\n\n");//$NON-NLS-1$

		try {
			RandomAccessFile out = new RandomAccessFile(file, "rw");//$NON-NLS-1$
			for (String line : lines) {
				out.write((line + "\n").getBytes());//$NON-NLS-1$
			}
			out.close();
		} catch (Exception e) {
			reporter.error(file + ": got exception while writing");
		}
	}

	private int findLine(String prefix, List<String> lines) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(prefix)) {
				return i;
			}
		}
		return -1;
	}

	private List<Integer> findLines(String prefix, List<String> lines,
			int startPos) {
		List<Integer> result = new ArrayList<Integer>();
		for (int i = startPos; i < lines.size(); i++) {
			if (lines.get(i).startsWith(prefix)) {
				result.add(i);
			}
		}
		return result;
	}

	private String getOutParametersAsString(String localCode, int nrParameters) {
		boolean[] tmp = new boolean[nrParameters];
		for (int i = 0; i < tmp.length; i++) {
			tmp[i] = false;
		}
		String[] lines = localCode.split("\n");//$NON-NLS-1$
		for (String line : lines) {
			if (line.trim().startsWith("reply.write") && line.contains("_arg")) {//$NON-NLS-1$//$NON-NLS-2$
				tmp[Integer.parseInt(line.substring(line.indexOf("_arg") + 4,//$NON-NLS-1$
						line.lastIndexOf(")")))] = true;//$NON-NLS-1$
			}
		}
		String result = "";//$NON-NLS-1$
		for (int i = 0; i < tmp.length; i++) {
			result += "                        " + tmp[i] + ",\n";//$NON-NLS-1$//$NON-NLS-2$
		}
		return result;
	}

	private String getOutParameterLines(String localCode) {
		String result = "";//$NON-NLS-1$
		String[] lines = localCode.split("\n");//$NON-NLS-1$
		for (String line : lines) {
			if (line.trim().startsWith("reply.write") && line.contains("_arg")) {//$NON-NLS-1$//$NON-NLS-2$
				result += "                   " + line.trim() + "\n";//$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return result;
	}

	private String getLocalCode(List<String> lines, int position) {
		String result = "           try {\n" + "               "//$NON-NLS-1$//$NON-NLS-2$
				+ lines.remove(position).replace("this.", "") + "\n";//$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
		do {
			// System.out.println("moving: " + lines.get(position));
			result += "               " + lines.remove(position) + "\n";//$NON-NLS-1$//$NON-NLS-2$
		} while (!lines.get(position).startsWith("return true;"));//$NON-NLS-1$
		result += "           } catch (android.os.RemoteException e) {}\n";//$NON-NLS-1$
		return result;
	}

	private String getParametersAsString(List<String> parameterTypes) {
		String result = "";//$NON-NLS-1$
		for (String parameterType : parameterTypes) {
			result += "                        " + stripGenerics(parameterType)//$NON-NLS-1$
					+ ".class,\n";//$NON-NLS-1$
		}
		return result;
	}

	private String getObjectsAsString(int size) {
		String result = "";//$NON-NLS-1$
		for (int i = 0; i < size - 1; i++) {
			result += "                        _arg" + i + ",\n";//$NON-NLS-1$//$NON-NLS-2$
		}
		result += "                        _arg" + (size - 1) + "\n";//$NON-NLS-1$//$NON-NLS-2$
		return result;
	}

	private String stripGenerics(String parameterType) {
		if (parameterType.indexOf("<") > 0) {//$NON-NLS-1$
			return parameterType.substring(0, parameterType.indexOf("<"));//$NON-NLS-1$
		} else {
			return parameterType;
		}
	}

	private String parseMethod(String line) {
		// System.out.println("parsing method: " + line);
		return (line.substring(line.indexOf("this.") + 5, line.indexOf('(')));//$NON-NLS-1$
	}

	private String getServiceName(List<String> lines) {
		for (String line : lines) {
			if (line.startsWith("private static final java.lang.String DESCRIPTOR = ")) {//$NON-NLS-1$
				String temp = line.split(" ")[6].substring(1,//$NON-NLS-1$
						line.split(" ")[6].indexOf("\";"));//$NON-NLS-1$//$NON-NLS-2$
				return temp.replace(temp.substring(temp.lastIndexOf(".")),//$NON-NLS-1$
						".remote" + temp.substring(temp.lastIndexOf(".")));//$NON-NLS-1$//$NON-NLS-2$

			}
		}
		return null;
	}

	private String parseWriteType(String line, String returnType) {
		// System.out.println("parsing writeType: " + line);
		if (returnType == null) {
			return null;
		}
		try {
			return line.substring(line.indexOf(".") + 6, line.indexOf("("));//$NON-NLS-1$//$NON-NLS-2$
		} catch (Exception e) {
			return null;
		}
	}

	private boolean isParcelable(String line, String returnType) {
		// System.out.println("isParcelable: " + line);
		if (returnType == null) {
			return false;
		}
		try {
			line.substring(line.indexOf(".") + 6, line.indexOf("("));//$NON-NLS-1$//$NON-NLS-2$
			return false;
		} catch (Exception e) {
			return true;
		}
	}

	private String parseReturnType(String line) {
		// System.out.println("parsing return type: " + line);
		if (line.startsWith("this.")) {//$NON-NLS-1$
			return null;
		}
		return line.substring(0, line.indexOf(" "));//$NON-NLS-1$
	}

	private String asObject(String type) {
		if (type.equals("int")) {//$NON-NLS-1$
			return "Integer";//$NON-NLS-1$
		}
		if (type.equals("long")) {//$NON-NLS-1$
			return "Long";//$NON-NLS-1$
		}
		if (type.equals("boolean")) {//$NON-NLS-1$
			return "Boolean";//$NON-NLS-1$
		}
		if (type.equals("short")) {//$NON-NLS-1$
			return "Short";//$NON-NLS-1$
		}
		if (type.equals("byte")) {//$NON-NLS-1$
			return "Byte";//$NON-NLS-1$
		}
		if (type.equals("float")) {//$NON-NLS-1$
			return "Float";//$NON-NLS-1$
		}
		if (type.equals("double")) {//$NON-NLS-1$
			return "Double";//$NON-NLS-1$
		}
		if (type.equals("char")) {//$NON-NLS-1$
			return "Char";//$NON-NLS-1$
		}
		return type;
	}

	private String protectParcel(String localCode) {
		return localCode
				.replace(
						"reply.writeNoException",
						"synchronized (done) {\n            	   if (!(done[0])) {\n            	       reply.writeNoException")
				.replace(
						"} catch",
						"        }\n            	   done[0] = true;\n            	   }\n            } catch");
	}

}
