package interdroid.cuckoo.eclipse.plugin;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.android.ide.eclipse.adt.AdtPlugin;

public class ClasspathContainerInitializer extends
	org.eclipse.jdt.core.ClasspathContainerInitializer {
    
    public ClasspathContainerInitializer() {
	// nothing
    }

    @Override
    public void initialize(IPath path, IJavaProject project) throws CoreException {
	AdtPlugin.printToConsole(project.getProject(), "ClasspathContainerInitializer.initialize()");//$NON-NLS-1$
	ClasspathContainer container = new ClasspathContainer();
	container.setProject(project);
        JavaCore.setClasspathContainer(path, new IJavaProject[] {project}, new IClasspathContainer[] {container}, null);
    }

    public static void updateProjects(IJavaProject[] projects) {
	ClasspathContainer[] containers = new ClasspathContainer[projects.length];
	for (int i = 0; i < projects.length; i++) {
	    containers[i] = new ClasspathContainer();
	    containers[i].setProject(projects[i]);
	    
	}
	try {
	    JavaCore.setClasspathContainer(
	            ClasspathContainer.ID, projects, containers, null);
	} catch (JavaModelException e) {
	    // ??
	}

    }
}
