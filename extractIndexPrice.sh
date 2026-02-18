matchstr=`date +"%b %d"`
line=`curl -s --url  https://www.google.com/finance/quote/.INX:INDEXSP | tail -n 20 | grep -o "data-last-price.*$matchstr................"`

# Extract the first 10 characters
first_10="${line:17:10}"

# Extract the last 15 characters (note the space before -15 for clarity)
last_15="${line: -22}"
echo "$first_10|$last_15"
