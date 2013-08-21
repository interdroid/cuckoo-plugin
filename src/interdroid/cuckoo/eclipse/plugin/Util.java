package interdroid.cuckoo.eclipse.plugin;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Util {
	
	public static List<String> getFileAsStringList(String file, ErrorReporter reporter) {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(
					file)));
		} catch (FileNotFoundException e) {
			// won't happen
			reporter.error(file + ": could not open for reading");
			return null;
		}
		List<String> lines = new ArrayList<String>();
		try {
			while (true) {
				String line = in.readLine();
				if (line == null)
					break;
				lines.add(line);
			}
			in.close();
		} catch (IOException e) {
			reporter.error(file + ": got exception while reading");
			return null;
		}
		return lines;
	}

}
