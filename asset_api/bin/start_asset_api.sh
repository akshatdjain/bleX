#! /bin/bash


#############################################################
# Author Ganesha Sridhara 				    #
# Date 03/Apr/2020					    #					    
# This shell script is provided for start / stop process    #
#	API_Portal application		            #
#############################################################


export SVTG_HOME=/opt/asset_tracking/asset_api


function start
{

	# If API is alread running display message and exit.
        if [ -f "$SVTG_HOME/logs/API_Portal.pid" ]
	then
		read pid <  $SVTG_HOME/logs/API_Portal.pid
		if [ `ps -p $pid | wc -l ` -gt 1 ]
		then
			echo "Asset Tracking -->  API Portal is running with Process ID=$pid"
			exit
		fi
	fi
	
	sleep 1;

	source /opt/myvenv/bin/activate
	cd $SVTG_HOME
	nohup /opt/myvenv/bin/python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000 > $SVTG_HOME/logs/API_Portal.out 2> $SVTG_HOME/logs/API_Portal.err &

	echo $! > $SVTG_HOME/logs/API_Portal.pid
	echo "Asset Tracking --> API_Portal started successfully."
}

function stop
{
if [ -d "$SVTG_HOME" ]
then
        if [ -f "$SVTG_HOME/logs/API_Portal.pid" ]
        then
                read pid <  $SVTG_HOME/logs/API_Portal.pid
                if [ `ps -p $pid | wc -l ` -gt 1 ]
                then
                        kill -15 $pid
                        sleep 2
                        # if not normal shutdown
                        if [ `ps -p $pid | wc -l ` -gt 1 ]
                        then
                                kill -9 $pid
                        fi
                        echo "Asset Tracking --> API_Portal stopped successfully."
                else
                         echo "Asset Tracking --> API_Portal is not running."
                fi
                rm -f $SVTG_HOME/logs/API_Portal.pid
        else
                echo "Asset Tracking --> API_Portal is not running."
        fi
fi
}

function status
{
if [ -d "$SVTG_HOME" ]
then
        if [ -f "$SVTG_HOME/logs/API_Portal.pid" ]
        then
                read pid <  $SVTG_HOME/logs/API_Portal.pid
                if [ `ps -p $pid | wc -l ` -gt 1 ]
                then
                        echo "Asset Tracking --> API_Portal is running with Process ID=$pid"
                else
                         echo "Asset Tracking --> API_Portal is not running."
                	 rm -f $SVTG_HOME/logs/API_Portal.pid
                fi
        else
                echo "Asset Tracking --> API_Portal is not running."
        fi
fi
}


case "$1" in
  start)
        start
        ;;
  stop)
        stop
        ;;
  status)
        status
        ;;
  restart)
        stop
        start
        ;;
  *)
        echo $"Usage: $0 {start|stop|restart|status}"
        exit 1
esac

