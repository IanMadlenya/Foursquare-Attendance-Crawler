FoursquareAttendanceCrawler
=============================

## Settings

Open the `etc/settings-example.json` file and change the `foursquare_api_accounts` and `crawl_folder` properties. You can obtain credentials for the Foursquare API by creating an application at https://foursquare.com/developers/apps.

After setting the parameters to the appropriate values, change the name of the file to `etc/settings.json`, otherwise the program will not find it:

```
  $ mv etc/settings-example.json etc/settings.json
```


## Selecting the venues to crawl

The first step is to scan the entire city in order to obtain a pool of all the venues of the city. Only venues with more than 25 overall checkins will be kept at this stage. To do this, you need to execute the `GetAllVenues` program:

```
  $ java -Dfile.encoding=UTF-8 -classpath bin:lib/commons-io-2.4.jar:lib/commons-lang-2.6.jar:lib/gson-1.7.1.jar eu.smartfp7.foursquare.GetAllVenues london
```

This step will likely take a lot of time (~12-24 hours), and will not end until you cancel the execution of the program. Do not cancel the execution if you haven't seen the following message:

> Finished crawling everything. Pausing 10 minutes before restarting...

The resulting list of venues will be written in a `.exhaustive_crawl` folder, created under the directory that you specified in the `etc/settings.json` file. Since the attendance crawler cannot deal with more than 5,000 venues, we need to filter the set of all venues:

```
  $ java -Dfile.encoding=UTF-8 -classpath bin:lib/commons-io-2.4.jar:lib/commons-lang-2.6.jar:lib/gson-1.7.1.jar eu.smartfp7.foursquare.FilterVenues london
```

This program will keep the 3,000 venues with the most number of checkins (i.e. the most popular venues), and will select 1,950 more venues from the remaining ones. This set of 4,950 venues will be the venues for which we will obtain hourly levels of attendance.



## Crawling the hourly attendance of venues

Simply run this program to start the crawling for all the venues:

```
  $ java -Dfile.encoding=UTF-8 -classpath bin:lib/commons-io-2.4.jar:lib/commons-lang-2.6.jar:lib/gson-1.7.1.jar eu.smartfp7.foursquare.AttendanceCrawler london
```

One file per venue will be created in the `attendances_crawl` directory, where each line corresponds to one observation per hour. These files can be read and parsed using the `RTimeSeries` class.

If you have several cities in your `settings.json` file, you can also launch the crawling for all of them at once:

```
  $ java -Dfile.encoding=UTF-8 -classpath bin:lib/commons-io-2.4.jar:lib/commons-lang-2.6.jar:lib/gson-1.7.1.jar eu.smartfp7.foursquare.AttendanceCrawler london amsterdam sanfrancisco shanghai glasgow
```



## What if the crawler misses some hours?
