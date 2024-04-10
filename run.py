from collections import defaultdict

import matplotlib.pyplot as plt
import requests


def aggregate_data(data):
    # trace_data = [(span['name'], span['duration']) for trace in data for span in trace]
    trace_data = []
    for trace in data:
        for span in trace:
            try:
                trace_data.append((span['name'], span['duration']))
            except:
                print(span)

    aggregated_data = defaultdict(lambda: {'sum': 0, 'count': 0})
    for span_name, duration in trace_data:
        aggregated_data[span_name]['sum'] += duration
        aggregated_data[span_name]['count'] += 1

    # average_durations = [(span_name, info['sum'] / info['count']) for span_name, info in
    #                      aggregated_data.items()]
    durations = [(span_name, info['sum']) for span_name, info in aggregated_data.items()]
    return sorted(durations, key=lambda x: x[1], reverse=True)


def show_diagram(data: list):
    labels = [item[0] for item in data]
    values = [item[1] for item in data]

    plt.figure(figsize=(10, 8))
    plt.barh(labels, values)
    plt.xlabel('Values')
    plt.ylabel('Operations')
    plt.title('Operations bar chart')
    plt.gca().invert_yaxis()
    plt.grid(True)
    plt.subplots_adjust(left=0.305, right=0.980, bottom=0.065, top=0.955)

    plt.show()


if __name__ == '__main__':
    response = requests.get('http://127.0.0.1:9411/zipkin/api/v2/traces?serviceName=Node&limit=1000')
    if response.status_code == 200:
        try:
            data = response.json()
            sorted_data = aggregate_data(data)
            show_diagram(sorted_data)
        except ValueError:
            print('JSON decoding error')
