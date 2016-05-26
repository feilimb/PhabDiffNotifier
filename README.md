# PhabDiffNotifier
Java based Desktop notifications application for Phabricator (http://phabricator.org/) for the purpose of receiving real-time Growl alerts on updates to Phabricator Differentials.<br>

<h3>Requirements:</h3>
The Growl application should be installed locally to receive the desktop Growl notifications published by this application.
Growl is available for Windows and Mac, see:<br>
Mac: http://growl.info/<br>
Windows: http://www.growlforwindows.com/gfw/default.aspx<br>


<h3>Configuration:</h3>
The file config.properties (see 'build' directory) should be updated to contain the required Phabricator server address and user token information. 
Examples of each property are shown within the committed config.properties file, and URLs are provided n where to retrieve 
the required values from your Phabricator web server.

<h3>Example Usage:</h3>
Note: a pre-built runnable JAR file is available in the 'build' directory.<br>
<pre>java -jar phabDiffNotifier.jar &lt;path_to_config.properties&gt;</pre>
