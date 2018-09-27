import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.plaf.metal.*;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Swing program that allows users to generate thumbnail images from full-size 
 * images. The program presents a simple GUI. Using the GUI users can select a
 * directory on disk that contains jpeg (.jpg) files. The program creates a 
 * subdirectory named "thumbnails", and generates and stores the thumbnail 
 * images in the new subdirectory. 
 * 
 * @author Brad Coleman
 *
 */
@SuppressWarnings("serial")
public class ThumbnailGeneratorApp extends JPanel {

	private JButton _startBtn;        // Button to start the thumbnail generation process.
	private JButton _cancelBtn;		  // Button to cancel thumbnail generation.
	private JTextArea _outputLog;  	  // Component to display in-progress messages.
	private JProgressBar _progressBar;
	
	private List<File> _imageFiles;	  // List of image files for which thumbnails should be generated.
	private File _outputDirectory;	  // Output directory for storing thumbnails.
	private File _directory;
	private ThumbnailWorker _thumbnailer;
	// Specify the look and feel to use by defining the LOOKANDFEEL constant
	// Valid values are: null (use the default), "Metal", "System", "Motif",
	// and "GTK"
	final static String LOOKANDFEEL = "System";

	// If you choose the Metal L&F, you can also choose a theme.
	// Specify the theme to use by defining the THEME constant
	// Valid values are: "DefaultMetal", "Ocean",  and "Test"
	final static String THEME = "Test";
	
	public ThumbnailGeneratorApp() {
		
		_startBtn = new JButton("Process");
		_cancelBtn = new JButton("Cancel");
		_outputLog = new JTextArea();
		_outputLog.setEditable(false);
		_progressBar = new JProgressBar(0, 100);
		_progressBar.setValue(0);
		_progressBar.setStringPainted(true);
		
		// Register a handler for Process buttons clicks.

		_startBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				
				// Use a FileChooser Swing component to allow the user to 
				// select a directory where images are stored.
				final JFileChooser fc = new JFileChooser();
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int returnVal = fc.showDialog(ThumbnailGeneratorApp.this, "Select");

				// Whenever the user selects a directory ...
		        if (returnVal == JFileChooser.APPROVE_OPTION) {
		            _directory = fc.getSelectedFile();
		            
		            // Created a subdirectory named "thumbnails" to store the
		            // generated thumbnails. If the subdirectory already exists
		            // no action is taken.
		            try {
		            	String pathname = _directory.getCanonicalPath() + File.separator + "thumbnails";
		            	_outputDirectory = new File(pathname);
		            	_outputDirectory.mkdir();
		            } catch(IOException e) {
		            	e.printStackTrace();
		            }
		            	
		            // Scan the selected directory for all files with a "jpg" 
		            // extension. Store these files in a List.
		            _imageFiles = new ArrayList<File>();
		            File[] contents = _directory.listFiles();
		            for(int i = 0; i < contents.length; i++) {
		            	File file = contents[i];
		            	String filename = file.getName();
		            	String extension = filename.substring(filename.lastIndexOf(".") + 1, filename.length());
		            	if(file.isFile() && extension.equals("jpg")) {
		            		_imageFiles.add(file);
		            	}
		            			
		            }
		            
		            // Set the enabled state for buttons.
		            _startBtn.setEnabled(false);
		            _cancelBtn.setEnabled(true);
		            
		            // clear the output log.
		            _outputLog.setText(null);
		            _outputLog.setFont(new Font("Arial",Font.ITALIC,12));
		            
		            // Set the cursor to busy.
		            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					_thumbnailer = new ThumbnailWorker();
					_thumbnailer.addPropertyChangeListener(new PropertyChangeListener() {
						@Override
						public void propertyChange(PropertyChangeEvent evt) {
							if ("progress" == evt.getPropertyName()) {
								int progress = (Integer) evt.getNewValue();
								_progressBar.setValue(progress);
							}
						}
					});
					_thumbnailer.execute();
		        } 	
			}
		});
		
		// Register a handler for Cancel button clicks.
		_cancelBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				_thumbnailer.cancel(true);
			}
		});
		
		// Construct the GUI. 
		JPanel controlPanel = new JPanel();
		controlPanel.add(_startBtn);
		controlPanel.add(_cancelBtn);
		controlPanel.add(_progressBar);
		_cancelBtn.setEnabled(false);
		
		JScrollPane scrollPaneForOutput = new JScrollPane();
		scrollPaneForOutput.setViewportView(_outputLog);
		
		setLayout(new BorderLayout());
		add(controlPanel, BorderLayout.NORTH);
		add(scrollPaneForOutput, BorderLayout.CENTER);
		setPreferredSize(new Dimension(400,300));
	}



	private class ThumbnailWorker extends SwingWorker<Void, String> {

		protected Void doInBackground() {
			//setProgress(int x) creates a PropertyChangeEvent with name "progress" and value x.
			// This is seen by the progressBar which has a PropertyChangeListener, progressBar
			// changes displayed value based on this event.
			setProgress(0);
			int i = 0;
			while (!isCancelled() && i < _imageFiles.size()) {
				try {
					createThumbnail(_imageFiles.get(i), _outputDirectory);
					publish("Processed " + _imageFiles.get(i).getName() + "\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
				i++;
				setProgress(100*i/_imageFiles.size());
			}
			if(isCancelled()) {
				publish("\n Thumbnail generation cancelled \n \n");
			}
			publish("Processed " + i + " out of " + _imageFiles.size() + " images in this directory.");
			return null;
		}

		protected void process (List<String> chunks) {
			for (String chunk : chunks) {
				_outputLog.append(chunk);
			}
		}

		protected void done() {
			_startBtn.setEnabled(true);
			_cancelBtn.setEnabled(false);
			setCursor(Cursor.getDefaultCursor());
		}

		/**
		 * Helper method to generate a thumbnail image for a particular image file.
		 *
		 * @param imageFile the source image file.
		 * @param outputDirectory the directory in which to store the generated thumbnail.
		 *
		 * @throws IOException if there is an error with loading images files or saving thumbnails.
		 *
		 */
		private void createThumbnail(File imageFile, File outputDirectory) throws IOException {
			BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
			img.createGraphics().drawImage(ImageIO.read(imageFile).getScaledInstance(100, 100, Image.SCALE_SMOOTH),0,0,null);

			File thumbnailFile = new File(outputDirectory.getCanonicalPath() + File.separator + imageFile.getName());
			ImageIO.write(img, "jpg", thumbnailFile);
		}
	}
	/**
	 * Helper method to display the GUI.
	 */
	private static void createAndShowGUI() {
		initLookAndFeel();
		// Create and set up the window.
		JFrame frame = new JFrame("Thumbnail Image Creator");

		// Create and set up the content pane.
		JComponent newContentPane = new ThumbnailGeneratorApp();
		frame.add(newContentPane);

		// Display the window.
		frame.pack();
        frame.setLocationRelativeTo(null); 
		frame.setVisible(true);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}
	
	public static void main(String[] args) {
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI();
			}
		});
	}

	private static void initLookAndFeel() {
		String lookAndFeel = null;

		if (LOOKANDFEEL != null) {
			if (LOOKANDFEEL.equals("Metal")) {
				lookAndFeel = UIManager.getCrossPlatformLookAndFeelClassName();
				//  an alternative way to set the Metal L&F is to replace the
				// previous line with:
				// lookAndFeel = "javax.swing.plaf.metal.MetalLookAndFeel";

			}

			else if (LOOKANDFEEL.equals("System")) {
				lookAndFeel = UIManager.getSystemLookAndFeelClassName();
			}

			else if (LOOKANDFEEL.equals("Motif")) {
				lookAndFeel = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
			}

			else if (LOOKANDFEEL.equals("GTK")) {
				lookAndFeel = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
			}

			else {
				System.err.println("Unexpected value of LOOKANDFEEL specified: "
						+ LOOKANDFEEL);
				lookAndFeel = UIManager.getCrossPlatformLookAndFeelClassName();
			}

			try {


				UIManager.setLookAndFeel(lookAndFeel);

				// If L&F = "Metal", set the theme

				if (LOOKANDFEEL.equals("Metal")) {
					if (THEME.equals("DefaultMetal"))
						MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
					else
						MetalLookAndFeel.setCurrentTheme(new OceanTheme());
					UIManager.setLookAndFeel(new MetalLookAndFeel());
				}




			}

			catch (ClassNotFoundException e) {
				System.err.println("Couldn't find class for specified look and feel:"
						+ lookAndFeel);
				System.err.println("Did you include the L&F library in the class path?");
				System.err.println("Using the default look and feel.");
			}

			catch (UnsupportedLookAndFeelException e) {
				System.err.println("Can't use the specified look and feel ("
						+ lookAndFeel
						+ ") on this platform.");
				System.err.println("Using the default look and feel.");
			}

			catch (Exception e) {
				System.err.println("Couldn't get specified look and feel ("
						+ lookAndFeel
						+ "), for some reason.");
				System.err.println("Using the default look and feel.");
				e.printStackTrace();
			}
		}
	}
}

