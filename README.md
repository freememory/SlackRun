#Challenge Run: The Slack Bot

This project encompasses the entirety of the bot functionality used as part of our
running group on Slack (freenoderunning.slack.com). It originally started as an IRC bot
some months ago in mid 2017 and evolved into a Slack bot.

The bot itself is written in Java on top of Vert.X 3.

## Features
Currently the bot really only has two features:
* /strava slash command: retrieves info and leaderboard of our Strava running group.
* /livetrack slash command: retrieves info from Garmin's website for all currently tracked users.

### Livetracking
Livetracking is kind of interesting so it needs to be mentioned separately. If you set up your
Garmin to e-mail the bot (challenge.runbot+<YOUR_SLACK_NICK>@gmail.com) when you start an
activity it will auto e-mail the bot. When the bot receives an e-mail it will start monitoring
your activity.

After a 2 minute grace period it will announce your activity. After that you will be added to the
/livetrack queue. /livetrack will show everyone running, /livetrack NICK will show some interesting
stats specific to you (i.e. your last pulled trackLog).

After you're done running it will announce the run.

## TODO
* Enhance the /strava command to take a club id or athlete id
* Enhance /livetrack to allow for imperial units
* Add more stuff.

