package interdroid.cuckoo.eclipse.plugin;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.build.builders.PreCompilerBuilder;
// import com.android.ide.eclipse.adt.internal.project.AndroidNature;

@SuppressWarnings("restriction")
public class CuckooNature /*extends AndroidNature*/ implements IProjectNature {

    /**
     * ID of this project nature.
     */
    public static final String NATURE_ID = "CuckooPlugin.CuckooNature";//$NON-NLS-1$

    private IProject project;

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.core.resources.IProjectNature#configure()
     */
    public void configure() throws CoreException {
	// First, configure the Android nature.
	AdtPlugin.printToConsole(project, "CuckooNature: configure()");//$NON-NLS-1$
	// super.configure();
	// Then, remove the PreCompilerBuilder. We'll replace that with our own.
	// removeBuilder(project, PreCompilerBuilder.ID);
	// Insert Cuckoo builder.
	configureCuckooBuilder();
    }
    
    /**
     * Adds the CuckooBuilder if its not already there. It will check for
     * the presence of a PreCompilerBuilder, and replace that. If not present,
     * it will just return.
     * @param project
     * @throws CoreException
     */
    private  void configureCuckooBuilder() throws CoreException {
        // get the builder list
        IProjectDescription desc = project.getDescription();
        ICommand[] commands = desc.getBuildSpec();

        // look for the builder in case it's already there.
        for (int i = 0; i < commands.length; ++i) {
            if (CuckooBuilder.ID.equals(commands[i].getBuilderName())) {
        	AdtPlugin.printToConsole(project, "CuckooNature: builder present");//$NON-NLS-1$
                return;
            }
        }

        // we need to replace the precompiler builder.
        // Let's look for it.

        for (int i = 0; i < commands.length; i++) {
            if (PreCompilerBuilder.ID.equals(commands[i].getBuilderName())) {
        	AdtPlugin.printToConsole(project, "CuckooNature: replacing PreCompilerBuilder");//$NON-NLS-1$
                ICommand command = desc.newCommand();
                command.setBuilderName(CuckooBuilder.ID);
                commands[i] = command;

                // set the new builders in the project
                desc.setBuildSpec(commands);
                project.setDescription(desc, null);
                return;
            }
        }
        AdtPlugin.printErrorToConsole(project, "CuckooNature: PreCompilerBuilder not found");//$NON-NLS-1$
    }

    /**
     * Replaces the CuckooBuilder with the PreCompilerBuilder.
     * @param project
     * @throws CoreException
     */
    private void configurePreCompilerBuilder() throws CoreException {
        // get the builder list
        IProjectDescription desc = project.getDescription();
        ICommand[] commands = desc.getBuildSpec();

        // look for the builder in case it's already there.
        for (int i = 0; i < commands.length; i++) {
            if (CuckooBuilder.ID.equals(commands[i].getBuilderName())) {
                ICommand command = desc.newCommand();
                command.setBuilderName(PreCompilerBuilder.ID);
                commands[i] = command;
                desc.setBuildSpec(commands);
                project.setDescription(desc, null);
                return;
            }
        }

        AdtPlugin.printErrorToConsole(project, "CuckooNature: CuckooBuilder not found");//$NON-NLS-1$
    }


    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.core.resources.IProjectNature#deconfigure()
     */
    public void deconfigure() throws CoreException {
	configurePreCompilerBuilder();
	// super.deconfigure();
	AdtPlugin.printToConsole(project, "CuckooNature: deconfigure");//$NON-NLS-1$
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.core.resources.IProjectNature#getProject()
     */
    public IProject getProject() {
	return project;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.core.resources.IProjectNature#setProject(org.eclipse.core
     * .resources.IProject)
     */
    public void setProject(IProject project) {
	// super.setProject(project);
	this.project = project;
    }

}
