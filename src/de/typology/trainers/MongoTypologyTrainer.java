package de.typology.trainers;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

import de.typology.interfaces.Trainable;

public class MongoTypologyTrainer implements Trainable {
	private int corpusId;
	private DB db;
	private NGramReader nGramReader;
	private NGram currentNGram;
	// private HashSet<String> nodes = new HashSet<String>();
	private List<Pair> currentListOfPairs;
	private Mongo m;

	public MongoTypologyTrainer(int corpusId) {
		this.corpusId = corpusId;
	}

	@Override
	public double train(NGramReader nGramReader) {
		try {
			this.m = new Mongo();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		long start_time = System.nanoTime();

		// mongodb initialization
		this.db = this.m.getDB("mydb");

		Set<String> colls = this.db.getCollectionNames();
		for (String s : colls) {
			System.out.println(s);
		}

		// create new edge collection
		BasicDBObject edgeCreateOptions = new BasicDBObject();
		edgeCreateOptions.append("capped", false);
		// edgeCreateOptions.append("size", 2147483648.0);
		DBCollection edgeCollection = this.db.createCollection("edges",
				edgeCreateOptions);

		this.nGramReader = nGramReader;

		int nGramCount = 0;

		while ((this.currentNGram = this.nGramReader.readNGram()) != null) {
			nGramCount++;

			if (nGramCount % 10000 == 0) {
				System.out
						.println(nGramCount
								+ " "
								+ Math.round((double) (System.nanoTime() - start_time) / 1000)
								/ 1000 + " ms");

			}

			for (int edgeType = 1; edgeType < this.currentNGram.getLength(); edgeType++) {
				// generate pairs of words with distance=edgeType
				this.currentListOfPairs = this.currentNGram
						.getPairsWithEdgeType(edgeType);
				for (Pair p : this.currentListOfPairs) {

					// iterate over all relationships with current edgeType
					String edgeID = p.getFirst() + "|" + edgeType + "|"
							+ p.getSecond();

					DBObject currentEdge = edgeCollection.findOne(edgeID);
					if (currentEdge == null) {
						// current relationship does not exist
						BasicDBObject newEdge = new BasicDBObject();
						newEdge.put("_id", edgeID);
						newEdge.put("cnt", this.currentNGram.getOccurrences());
						edgeCollection.insert(newEdge);
					} else {
						// current relationship does exist
						BasicDBObject set = new BasicDBObject("$set",
								new BasicDBObject("cnt",
										(Integer) currentEdge.get("cnt")
												+ this.currentNGram
														.getOccurrences()));
						edgeCollection.update(currentEdge, set);
					}
				}
			}
		}
		long end_time = System.nanoTime();
		return Math.round((double) (end_time - start_time) / 1000) / 1000;
	}

	@Override
	public int getCorpusId() {
		return this.corpusId;
	}

	@Override
	public void setCorpusId(int corpusId) {
		this.corpusId = corpusId;
	}

	public void writeDB(String path) throws IOException {
		Writer writer = new OutputStreamWriter(new FileOutputStream(path));
		this.m = new Mongo();
		// mongodb initialization
		this.db = this.m.getDB("mydb");
		DBCollection coll = this.db.getCollection("edges");
		DBCursor cursor = coll.find();
		try {
			while (cursor.hasNext()) {
				writer.write(cursor.next() + "\n");
				writer.flush();
			}
		} finally {
			cursor.close();
			writer.close();
		}

	}
}
