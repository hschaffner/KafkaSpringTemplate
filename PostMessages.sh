  curl -X POST -H "Content-Type: application/json" -d @data.json http://localhost:9099/test


#Sample of data.json file
  #{
    #	"first_name":"Heinz",
    #	"last_name":"Schaffner",
    #	"customer_id":1234567890
    #}



#Shell script to Post lots of messages
#c=0
#while [ $c -le 100 ]
#do
#    curl -X POST -H "Content-Type: application/json" -d @data.json http://localhost:9099/test
#    c=$((c+1))
#done

# Check number of consumers for group for retry topic
# confluent kafka consumer list --group KafkaTemplateID-retry-1