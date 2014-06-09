python run.py --compile
if [ "$?" != "0" ]; then
	python run.py --run --T 500 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UA --learning adagrad -v 
fi
