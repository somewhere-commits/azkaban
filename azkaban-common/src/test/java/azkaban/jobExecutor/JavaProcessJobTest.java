/*
 * Copyright 2014 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.jobExecutor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import azkaban.flow.CommonJobProperties;
import azkaban.jobExecutor.utils.process.ProcessFailureException;
import azkaban.utils.Props;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


public class JavaProcessJobTest {

  private static final String inputContent =
      "Quick Change in Strategy for a Bookseller \n"
          + " By JULIE BOSMAN \n"
          + "Published: August 11, 2010 \n"
          + " \n"
          + "Twelve years later, it may be Joe Fox's turn to worry. Readers have gone from skipping small \n"
          + "bookstores to wondering if they need bookstores at all. More people are ordering books online  \n"
          + "or plucking them from the best-seller bin at Wal-Mart";
  private static final String errorInputContent =
      inputContent
          + "\n stop_here "
          + "But the threat that has the industry and some readers the most rattled is the growth of e-books. \n"
          + " In the first five months of 2009, e-books made up 2.9 percent of trade book sales. In the same period \n"
          + "in 2010, sales of e-books, which generally cost less than hardcover books, grew to 8.5 percent, according \n"
          + "to the Association of American Publishers, spurred by sales of the Amazon Kindle and the new Apple iPad. \n"
          + "For Barnes & Noble, long the largest and most powerful bookstore chain in the country, the new competition \n"
          + "has led to declining profits and store traffic.";
  @ClassRule
  public static TemporaryFolder classTemp = new TemporaryFolder();
  private static String classPaths;
  private static String inputFile;
  private static String errorInputFile;
  private static String outputFile;
  private final Logger log = Logger.getLogger(JavaProcessJob.class);
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  private JavaProcessJob job = null;
  private Props props = null;
  private File workingDir;

  @BeforeClass
  public static void init() throws IOException {
    azkaban.test.Utils.initServiceProvider();
    // Get the classpath
    final Properties prop = System.getProperties();
    classPaths =
        String.format("'%s'", prop.getProperty("java.class.path", null));

    final long time = (new Date()).getTime();
    inputFile = classTemp.newFile("azkaban_input_" + time).getCanonicalPath();
    errorInputFile =
        classTemp.newFile("azkaban_input_error_" + time).getCanonicalPath();
    outputFile = classTemp.newFile("azkaban_output_" + time).getCanonicalPath();

    // Dump input files
    try {
      Utils.dumpFile(inputFile, inputContent);
      Utils.dumpFile(errorInputFile, errorInputContent);
    } catch (final IOException e) {
      e.printStackTrace(System.err);
      Assert.fail("error in creating input file:" + e.getLocalizedMessage());
    }
  }

  @AfterClass
  public static void cleanup() {
    classTemp.delete();
  }

  @Before
  public void setUp() throws IOException {
    this.workingDir = this.temp.newFolder("testJavaProcess");

    // Initialize job
    this.props = AllJobExecutorTests.setUpCommonProps();
    this.props.put(AbstractProcessJob.WORKING_DIR, this.workingDir.getCanonicalPath());
    this.props.put("type", "java");

    this.job = new JavaProcessJob("testJavaProcess", this.props, this.props, this.log);
  }

  @After
  public void tearDown() {
    this.temp.delete();
  }

  @Test
  public void testJavaJob() throws Exception {
    // initialize the Props
    this.props.put(JavaProcessJob.JAVA_CLASS,
        "azkaban.jobExecutor.WordCountLocal");
    this.props.put("input", inputFile);
    this.props.put("output", outputFile);
    this.props.put("classpath", classPaths);
    this.job.run();
  }

  @Test
  public void testJava11Job() throws Exception {
    this.props.put(JavaProcessJob.JAVA11_BINARY_PATH,
        "java");
    this.props.put(JavaProcessJob.RUN_ON_JAVA11_PARAMS, "true");
    this.props.put(JavaProcessJob.JAVA_CLASS,
        "azkaban.jobExecutor.WordCountLocal");
    this.props.put("input", inputFile);
    this.props.put("output", outputFile);
    this.props.put("classpath", classPaths);

    this.job.run();
  }

  @Test
  public void noClassPath() throws Exception {
    copyJarToJobDirectory();
    this.props.put(JavaProcessJob.JAVA_CLASS, "azkaban.jobExecutor.WordCountLocal");
    assertThatThrownBy(() -> this.job.run())
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(ProcessFailureException.class);
  }

  @Test
  public void emptyClassPath() throws Exception {
    copyJarToJobDirectory();
    this.props.put(JavaProcessJob.JAVA_CLASS, "azkaban.jobExecutor.WordCountLocal");
    this.props.put("classpath", "");
    assertThatThrownBy(() -> this.job.run())
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(ProcessFailureException.class);
  }

  @Test
  public void noClassPathNoJar() throws Exception {
    this.props.put(JavaProcessJob.JAVA_CLASS, "azkaban.jobExecutor.WordCountLocal");
    assertThatThrownBy(() -> this.job.run())
        .isInstanceOf(Exception.class)
        .hasMessageContaining("No classpath defined and no .jar files found")
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void emptyClassPathNoJar() throws Exception {
    this.props.put(JavaProcessJob.JAVA_CLASS, "azkaban.jobExecutor.WordCountLocal");
    this.props.put("classpath", "");
    assertThatThrownBy(() -> this.job.run())
        .isInstanceOf(Exception.class)
        .hasMessageContaining("No classpath defined and no .jar files found")
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testJavaJobHashmap() throws Exception {
    // initialize the Props
    this.props.put(JavaProcessJob.JAVA_CLASS,
        "azkaban.executor.SleepJavaJob");
    this.props.put("seconds", 0);
    this.props.put("input", inputFile);
    this.props.put("output", outputFile);
    this.props.put("classpath", classPaths);
    this.job.run();
  }

  @Test
  public void testFailedJavaJob() throws Exception {
    this.props.put(JavaProcessJob.JAVA_CLASS,
        "azkaban.jobExecutor.WordCountLocal");
    this.props.put("input", errorInputFile);
    this.props.put("output", outputFile);
    this.props.put("classpath", classPaths);

    try {
      this.job.run();
    } catch (final RuntimeException e) {
      Assert.assertTrue(true);
    }
  }

  private void copyJarToJobDirectory() throws IOException {
    // this jar doesn't really contain any class with a main() method
    // so the job will fail - this is just so that javaprocess finds it and sets '-cp test.jar'
    FileUtils.copyFileToDirectory(new File("src/test/resources/project/testfailure/test.jar"),
        this.workingDir);
  }

}
