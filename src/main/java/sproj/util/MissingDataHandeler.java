package sproj.util;

import java.util.ArrayList;
import java.util.List;

public class MissingDataHandeler {


    // TODO: 11/20/18 you also need to check to make sure the data isn't started over 
    // TODO: 11/20/18   eg in IMG_5193_pts.dat the data starts over again over the 40 ms mark

    private class Node {

        Node prev;
        Node next;
        Integer[] data;

        private Node(Integer[] d) {
            this.prev = null;
            this.next = null;
            this.data = d;
        }

        private double proximityTo(Node n) {
            return Math.pow(
                    Math.pow(n.data[0] - this.data[0], 2) + Math.pow(n.data[1] - this.data[1], 2), 2
            );
        }
    }

    private List<List<Integer[]>> fillInData(List<List<Integer[]>> dataPoints, int numbAnimals) {

        List<Integer[]> startingPoints = dataPoints.get(0);

        assert startingPoints.size() == numbAnimals + 1;   // +1 for time stamp

        List<Node> headNodes = new ArrayList<>(numbAnimals);
        for (int i=0; i<numbAnimals; i++) {
            headNodes.add(new Node(startingPoints.get(i)));
        }

    }

}
